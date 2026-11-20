# LLMia · app Android (v0.4.0)

App Kotlin + Jetpack Compose que hace dos cosas con la ESP32-CAM **sin reprogramar su firmware**:

1. **Maneja el robot** por la red local `Carrito_CAM`, con flechas, texto o voz.
2. **Hace de puente** entre la cámara y Cloud Run, para que Gemini describa lo que ve («¿Qué ves?»).

> Estado: cámara, distancia y puente anterior con token reportados funcionando por Julius en Android físico. Julius reportó también el flujo v0.3 funcionando. La integración v0.4 (contexto y búsqueda con servo) requiere todavía su prueba física. Ver el [punto de control actual](../docs/PUNTO_DE_CONTROL_2026-11-19_CONTEXTO_BUSQUEDA.md).

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
2. Android Studio → **Open** → carpeta `android/` de este repositorio → esperar **Sync**.
3. Ejecutar en un **Android físico** con Wi-Fi y datos móviles (Android 10+).
4. En **Ajustes**: escribir la clave de `Carrito_CAM` → **Conectar al robot** → aceptar el diálogo de Android.
5. Probar con ruedas al aire: flechas, STOP, «avanza un poco», «para».

### Para «¿Qué ves?» desde el teléfono (opcional)

Sin Firebase la app puede manejar el robot; el puente requiere iniciar sesión.

1. En la consola de Firebase del proyecto `proyectopersonal-480420`: **Agregar app → Android**, paquete `pe.edu.upn.llmia`, con la **huella SHA-1** de depuración (Android Studio → Gradle → `signingReport`).
2. Habilitar **Google** en Authentication (ya lo usa el panel web).
3. Copiar `local.properties.example` como `local.properties` en la raíz (no se versiona). Completar los valores sin comentarios al final de las líneas:

```properties
llmia.firebase.apiKey=...
llmia.firebase.appId=...
llmia.firebase.projectId=proyectopersonal-480420
llmia.firebase.webClientId=...
```

4. Usar cualquier cuenta de Google cuyo **UID de Firebase** esté en `LLMIA_ALLOWED_UIDS` del backend. Un compañero puede usar otro dispositivo y su propia cuenta: primero inicia sesión y luego el administrador incorpora su UID, conservando los UID existentes. No se autoriza por nombre ni por correo mostrado.
5. En la app: **Iniciar sesión con Google**, **Comprobar Cloud Run** e **Iniciar puente**. No se introduce un token. Luego decir «¿Qué ves?» o «Busca un perro y descríbelo».

Requiere desplegar primero el backend v0.3: las nuevas rutas del puente están bajo `/api/v1/robots/llmia-01/bridge`. Usan el ID token de Firebase, obtenido por el SDK antes de cada petición y renovado si corresponde. El backend verifica firma, proyecto, revocación y UID permitido. El secreto permanente del dispositivo no se entrega a Android; queda para el simulador y clientes anteriores. Cada puente atiende solamente consultas creadas por su misma cuenta, incluso si otra cuenta usa el panel web.

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

La clave Wi-Fi se escribe en Ajustes y vive solo en memoria. Firebase administra la persistencia y renovación de la sesión. No se copian ni registran tokens en logs. Cerrar sesión detiene el puente. `local.properties` no se sube a Git. El secreto del dispositivo sigue disponible solo para clientes anteriores y demos supervisadas.

## Cambios de control en v0.2.1

- Negaciones, alternativas, secuencias y tiempos cero/negativos no inician movimientos. El intérprete usa una gramática explícita; las frases desconocidas se rechazan. El reconocimiento de audio puede requerir Internet aunque el intérprete sea local.
- Fotos, distancia y servo no reservan la red durante una maniobra. Una foto ya en curso puede retrasar el inicio de otra maniobra; STOP comparte la misma cola HTTP para no quedar seguido por un MOVE antiguo.
- Mantener pulsado usa los mismos topes por dirección: 3 s adelante, 1 s atrás, 1,5 s en giros. Hay que soltar y volver a pulsar. Son duraciones objetivo del cliente, no temporizadores físicos de la placa.
- Al soltar o cancelar un gesto se solicita STOP en `finally`. Cancelar la voz invalida los callbacks anteriores.
- Una captura solicitada desde la nube detiene primero la maniobra. Cada imagen pertenece a un ID concreto: una solicitud vencida sin foto no muestra la foto anterior.
- El puente espera la cancelación anterior antes de reiniciar. Valida CAPTURE, id y vencimiento. Cloud Run y robot están restringidos a los destinos aprobados de esta demo.

### Verificación antes del uso físico

En Windows: `gradlew.bat testDebugUnitTest assembleDebug lintDebug`. Sin datos Firebase, el APK permite probar control local; el inicio de sesión requiere registrar la app Android y su SHA-1 en Firebase. El puente utiliza la sesión Firebase; volver del segundo plano requiere reactivarlo.

Probar STOP con cámara lenta/sin respuesta, pérdida de Wi-Fi, cancelación de gesto, cambio de pantalla y resultado de voz tardío. Medir la parada ante un obstáculo durante el avance. No se da por aprobada ninguna de estas pruebas con el montaje real.


## Probar Cloud Run desde el emulador

La red celular sigue siendo la ruta normal. Para permitir Internet predeterminado solo en debug, añade a tu `local.properties` existente (conserva `sdk.dir` y los cuatro valores Firebase):

```properties
llmia.debug.allowDefaultCloudNetwork=true
```

Sincroniza Gradle y vuelve a ejecutar la variante debug. En Ajustes aparece «Nube (Internet de prueba)». Pulsa «Comprobar Cloud Run»: verifica `/health`; si hay sesión de operador también consulta `GET /api/v1/robots/llmia-01/status` con su token Firebase. HTTP 200 muestra «Operador autorizado» aunque el robot esté desconectado. HTTP 401 indica sesión rechazada; HTTP 403 indica que ese UID no está autorizado por el backend. No se imprime el token ni se crean observaciones.

El modo requiere una red predeterminada con capacidades INTERNET y VALIDATED y excluye la red asignada al robot. Si hay celular validado, se mantiene su prioridad. Las llamadas siguen vinculadas por petición con `Network.openConnection`, sin cambiar la red global del proceso. El servidor HTTPS aprobado, la validación TLS y las credenciales se conservan.

Para volver al modo normal, cambia el valor a `false` y recompila. Las variantes release fuerzan `false` aunque el archivo local tenga `true`. Este ajuste no conecta el emulador a Carrito_CAM, no habilita motores ni convierte la prueba de nube en una prueba de hardware.


## Consultas visuales v0.3.0

Texto y voz comparten la misma interpretación. Ejemplos: «Busca un perro y descríbelo», «¿De qué color es esa silla?» y «¿Hay un perro o un gato?». Cada consulta crea un ID y captura nuevos; no reutiliza una foto anterior. Máximo 500 caracteres. Si el objeto no es visible, el modelo debe decirlo. En v0.3, buscar examinaba solo el encuadre actual. En v0.4, las órdenes explícitas «busca…» revisan hasta tres posiciones de cámara, como se detalla abajo; el carrito permanece detenido. Los movimientos siguen siendo órdenes locales explícitas y Gemini no tiene herramientas de control.

El backend conserva la pregunta en el registro de esa observación y la pasa al adaptador de visión con el JPEG correspondiente. Si un cliente antiguo no envía pregunta, se aplica «¿Qué ves?». El modo simulador conserva sus respuestas de colores: no reconoce objetos.

La versión nueva no está desplegada por el solo hecho de publicar código. Primero actualizar Cloud Run con la imagen de este commit, después instalar Android. No habilitar Cloud Testing ni Tool Results; las verificaciones usan pruebas JVM/compilación y el teléfono propio.

## Conversación con contexto y búsqueda v0.4

«¿Qué ves?» → «¿Y de qué color es?» conserva hasta tres intercambios propios en el backend. Cada pregunta toma una foto nueva; el historial no demuestra que el objeto siga visible. Usa «Nueva conversación» (botón o voz) para empezar sin contexto previo. Cerrar sesión también lo reinicia. Si una referencia fue eliminada por el límite de retención, inicia una conversación nueva.

«Busca un perro y descríbelo» o «Encuentra una botella con la cámara» inicia una búsqueda finita: STOP confirmado, cámara al centro/izquierda/derecha y hasta tres fotos analizadas. No se mueven ruedas. Cada posición espera al servo y vacía los buffers antes de la foto de consulta. Un hallazgo detiene la secuencia; ausencia e incertidumbre se distinguen. El sensor y vista previa se pausan durante la búsqueda y se reanudan al finalizar.

STOP cancela la búsqueda. La app bloquea conducción, servo manual y nuevas consultas durante la secuencia. No puede cancelar físicamente un objetivo de servo ya enviado ni una inferencia aceptada por Cloud Run. No uses simultáneamente otro controlador del carrito. Máximo tres inferencias por búsqueda; validar en el montaje los tiempos y el campo visual del servo. El modo simulador no detecta objetos y devuelve incertidumbre.

Requiere desplegar el backend 0.4 antes de instalar Android 0.4. No requiere cargar firmware nuevo. Ver [aceptación y punto de control](../docs/PUNTO_DE_CONTROL_2026-11-19_CONTEXTO_BUSQUEDA.md).
