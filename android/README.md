# LLMia · app Android (v0.2.1)

App Kotlin + Jetpack Compose que hace dos cosas con la ESP32-CAM **sin reprogramar su firmware**:

1. **Maneja el robot** por la red local `Carrito_CAM`, con flechas, texto o voz.
2. **Hace de puente** entre la cámara y Cloud Run, para que Gemini describa lo que ve («¿Qué ves?»).

> Estado: correcciones de integración y control, con pruebas automatizadas. La prueba física del Android con el robot sigue pendiente. Comenzar con los motores desconectados y luego con las ruedas elevadas.

## Qué trae la interfaz

| Zona | Qué hace |
| --- | --- |
| Vista en vivo | Foto de la cámara cada ~0,8 s con HUD: esquinas, mira, línea de barrido, distancia frontal y destello rojo si hay obstáculo. Toca «EN VIVO» para pausar. |
| Conducir | Radar de distancia (anillo rojo = 20 cm), cruceta **mantener pulsado** con STOP al centro, control de la cámara (servo 0–180°) y barrido. |
| Conversar | Chat con LLMia: tus órdenes, sus respuestas, la foto que analizó Gemini y el avance de cada solicitud. |
| Barra inferior | STOP siempre visible, campo de texto, micrófono animado y atajos («¿Qué ves?», «Avanza un poco»…). |
| Ajustes | Conectar Carrito_CAM, comprobar Cloud Run, iniciar el puente y sesión del operador. |

LLMia responde en voz alta (síntesis del sistema).

## Órdenes por texto o voz

El **teléfono** interpreta el texto con reglas fijas (`command/CommandParser.kt`) sin consultar Internet. El reconocimiento de audio depende del servicio Android y puede requerir datos móviles. Gemini nunca decide un movimiento.

| Ejemplo | Resultado |
| --- | --- |
| «avanza», «sigue derecho» | Avanza 0,9 s |
| «avanza un poco» / «avanza mucho» | 0,45 s / 1,6 s |
| «avanza 2 segundos», «avanza dos segundos» | 2 s (tope 3 s) |
| «gira a la izquierda», «dobla a la derecha» | Giro de 0,4 s (tope 1,5 s) |
| «da media vuelta a la derecha» | Giro de 0,9 s (calibrar) |
| «retrocede» | 0,5 s (tope 1 s: atrás no hay sensor) |
| «para», «alto», «detente», «frena» | Solicita STOP al reconocer la orden; sin garantía de latencia |
| «¿qué ves?», «describe la escena» | Foto nueva → Gemini → respuesta hablada |
| «mira a la izquierda», «cámara al centro» | Mueve el servo, no las ruedas |
| «activa el barrido» / «para el barrido» | Barrido automático de la cámara |

Los tiempos están en `CommandParser.TIMES` y deben **calibrarse** con el robot en el piso.

## Seguridad del movimiento

- Las órdenes van **solo** por la red local a `http://192.168.4.1/move` (ruta existente del firmware).
- La app espera 200 ms entre respuestas de movimiento y termina solicitando `STOP`. Revoca órdenes en cola antes de parar. El watchdog del firmware comprueba 700 ms en su bucle principal; su latencia real depende también del servidor HTTP y requiere medición física.
- La placa rechaza «adelante» si el sensor marca 20 cm o menos, o no tiene lectura; la app lo muestra y lo dice.
- Al pasar a segundo plano, cancela la voz, bloquea nuevas maniobras y solicita STOP. El puente se apaga y debe reactivarse en Ajustes al volver.
- **Pendiente antes de la demo:** probar la parada cuando aparece un obstáculo *después* de iniciar la marcha y la parada al perder el Wi-Fi (regla de la SPEC). Retroceso y giros no tienen sensor.

## Abrir y ejecutar

1. En PowerShell, dentro de la carpeta: `powershell -ExecutionPolicy Bypass -File .\bootstrap-wrapper.ps1`.
2. Android Studio → **Open** → carpeta `LLMiaAndroid` → esperar **Sync**.
3. Ejecutar en un **Android físico** con Wi-Fi y datos móviles (Android 10+).
4. En **Ajustes**: escribir la clave de `Carrito_CAM` → **Conectar al robot** → aceptar el diálogo de Android.
5. Probar con ruedas al aire: flechas, STOP, «avanza un poco», «para».

### Para «¿Qué ves?» desde el teléfono (opcional)

Sin esto la app igual maneja el robot y funciona como puente para el panel web.

1. En la consola de Firebase del proyecto `proyectopersonal-480420`: **Agregar app → Android**, paquete `pe.edu.upn.llmia`, con la **huella SHA-1** de depuración (Android Studio → Gradle → `signingReport`).
2. Habilitar **Google** en Authentication (ya lo usa el panel web).
3. Copiar `local.properties.example` como `local.properties` en la raíz (no se versiona). Completar los valores sin comentarios al final de las líneas:

```properties
llmia.firebase.apiKey=...
llmia.firebase.appId=...
llmia.firebase.projectId=proyectopersonal-480420
llmia.firebase.webClientId=...
```

4. Usar la **misma cuenta de Google** que ya está en `LLMIA_ALLOWED_UIDS` del backend.
5. En la app: **Iniciar sesión con Google**, **Comprobar Cloud Run**, ingresar el token del dispositivo e **Iniciar puente**. Luego decir «¿Qué ves?».

El backend no cambia: la app usa el mismo contrato que el panel web (`POST /api/v1/robots/llmia-01/observations`) y el del dispositivo (`/device/v1/poll` y `PUT …/capture`).

## Archivos

| Archivo | Responsabilidad |
| --- | --- |
| `net/Links.kt` | Wi-Fi del robot y red celular, elegidas por petición |
| `net/RobotApi.kt` | Rutas del firmware: `/move`, `/distance`, `/capture`, `/servo`, `/sweep` |
| `net/CloudApi.kt` | Contrato de Cloud Run (operador y dispositivo) |
| `command/CommandParser.kt` | Intérprete local de órdenes (con pruebas en `src/test`) |
| `voice/Voice.kt` | Reconocimiento y síntesis de voz del sistema |
| `auth/OperatorAuth.kt` | Inicio de sesión del operador con Google/Firebase |
| `LlmiaViewModel.kt` | Estado, movimiento seguro, puente y «¿Qué ves?» |
| `ui/*` | Tema, vista en vivo, radar, cruceta, chat y ajustes |

## Credenciales

La clave Wi-Fi y el token del dispositivo se escriben en Ajustes, viven solo en memoria y no se guardan. `local.properties` no se sube a Git. El token compartido del dispositivo sigue siendo solo para demos supervisadas.

## Cambios de control en v0.2.1

- Negaciones, alternativas, secuencias y tiempos cero/negativos no inician movimientos. El intérprete usa una gramática explícita; las frases desconocidas se rechazan. El reconocimiento de audio puede requerir Internet aunque el intérprete sea local.
- Fotos, distancia y servo no reservan la red durante una maniobra. Una foto ya en curso puede retrasar el inicio de otra maniobra; STOP comparte la misma cola HTTP para no quedar seguido por un MOVE antiguo.
- Mantener pulsado usa los mismos topes por dirección: 3 s adelante, 1 s atrás, 1,5 s en giros. Hay que soltar y volver a pulsar. Son duraciones objetivo del cliente, no temporizadores físicos de la placa.
- Al soltar o cancelar un gesto se solicita STOP en `finally`. Cancelar la voz invalida los callbacks anteriores.
- Una captura solicitada desde la nube detiene primero la maniobra. Cada imagen pertenece a un ID concreto: una solicitud vencida sin foto no muestra la foto anterior.
- El puente espera la cancelación anterior antes de reiniciar. Valida CAPTURE, id y vencimiento. Cloud Run y robot están restringidos a los destinos aprobados de esta demo.

### Verificación antes del uso físico

En Windows: `gradlew.bat testDebugUnitTest assembleDebug lintDebug`. Sin datos Firebase, el APK permite probar control local y puente; el inicio de sesión requiere registrar la app Android y su SHA-1 en Firebase. El token de dispositivo se aprovisiona en memoria y no caduca por el solo hecho de cerrar la app: su revocación se administra en el backend.

Probar STOP con cámara lenta/sin respuesta, pérdida de Wi-Fi, cancelación de gesto, cambio de pantalla y resultado de voz tardío. Medir la parada ante un obstáculo durante el avance. No se da por aprobada ninguna de estas pruebas con el montaje real.
