# Arquitectura de la app Android LLMia (v0.2)

**Dos caminos separados:**

1. **Movimiento (local):** app → Wi-Fi `Carrito_CAM` → `GET http://192.168.4.1/move?dir=…` del firmware existente.
   No pasa por Internet ni por la nube. Lo usan las flechas, el texto y la voz.
2. **Visión (nube):** app → `/capture` por Wi-Fi local → `PUT /device/v1/observations/{id}/capture` por datos móviles →
   Cloud Run → Gemini → descripción de vuelta a la app y al panel web.

El firmware instalado no se reprograma. Cada petición elige su red con `Network.openConnection`
(`WifiNetworkSpecifier` para el robot, red celular validada para Cloud Run); nunca `bindProcessToNetwork`.

## Interpretación de órdenes

`command/CommandParser.kt` traduce texto o voz a una orden cerrada: mover (dirección + duración con tope),
parar, describir, mirar con el servo o barrido. Es determinístico y local. **Gemini no tiene herramientas
para accionar motores** y no participa en las órdenes de movimiento (regla de la SPEC).

## Seguridad del movimiento (en capas)

| Capa | Mecanismo |
| --- | --- |
| Placa | Bloquea «adelante» a 20 cm o sin lectura; comprueba 700 ms sin órdenes desde el bucle principal; latencia física por validar |
| App | Espera 200 ms entre respuestas, revoca órdenes en cola, solicita STOP y aplica topes por dirección también al mantener pulsado; cancela voz en segundo plano |
| Voz | «para / alto / detente» solicita STOP al reconocerse; no garantiza una latencia física |
| Operación | Demo en área plana, con supervisión; retroceso y giros sin sensor |

Pendiente (SPEC): probar la parada ante un obstáculo que aparece después de iniciar la marcha y ante la pérdida del Wi-Fi.

## «¿Qué ves?» desde el teléfono

El operador inicia sesión con Google en el mismo proyecto Firebase que el panel web. La app crea la observación
(`POST /api/v1/robots/llmia-01/observations`), el puente de la misma app la reclama (`/device/v1/poll`), sube una foto
nueva y recibe la descripción en la respuesta del `PUT`. Si la observación nace en el panel web, la app también muestra
el resultado. El backend no cambia.

## Credenciales

Clave Wi-Fi y token del dispositivo solo en memoria. Configuración de Firebase en `local.properties` (no versionado).
El token compartido del dispositivo se aprovisiona en memoria para demos supervisadas. No es una credencial temporal por sí misma: requiere revocación en el backend.
