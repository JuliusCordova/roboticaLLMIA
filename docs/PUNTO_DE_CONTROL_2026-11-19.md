# Punto de control — LLMia Android v0.2.1

Fecha: 19 de noviembre de 2026 (Perú). Rama: `fix/android-v021-control`. Este documento queda asociado al mismo commit que incorpora la app corregida. Base de integración: `f2a519c67f470f6f8d3734661b56913c205f28a3`.

## Trabajo completado

Se incorporó el proyecto Android recibido en el ZIP bajo `android/`. Se corrigieron permisos de red y ubicación, interpretación de instrucciones, ordenamiento MOVE/STOP, cancelación de voz y gestos, ciclo de vida, límites por dirección, reinicio del puente, asociación foto/observación y la animación de cámara detectada por lint. Se agregaron pruebas de regresión y un workflow de Android. El detalle está en [android-v021-validation.md](android-v021-validation.md).

## Lo que sigue pendiente

- Registrar/configurar la app Android `pe.edu.upn.llmia` en Firebase con SHA-1 y cliente OAuth Web del proyecto aprobado. No usar el appId web como appId Android. Crear `local.properties` a partir del ejemplo y completar solo en la copia local.
- Probar Android físico con Wi-Fi Carrito_CAM y datos móviles simultáneos. Las capturas del emulador anteriores no acreditan ese enlace.
- Validar físicamente STOP, pérdida de Wi-Fi, resultado de voz tardío, cancelación de gesto, captura lenta y obstáculo que aparece durante el avance. Primero motores desconectados y luego ruedas elevadas.
- Probar foto real hasta Gemini y las cinco escenas de aceptación, registrando ID, esperado, observado, latencia y evidencia.
- Revisar la protección local del firmware si las mediciones muestran retrasos: su watchdog se comprueba desde loop(), después de atender HTTP. La app no puede garantizar una parada física de 700 ms.

## Infraestructura y credenciales

No se modificó firmware, Cloud Run, IAM, Firestore, Storage ni Secret Manager en este incremento. Se conserva el destino aprobado `https://llmia-demo-913797829604.us-central1.run.app`, robot `llmia-01`, proyecto `proyectopersonal-480420`. La evidencia previa de Gemini corresponde a imágenes simuladas; no se presenta como prueba nueva de hardware.

La clave Wi-Fi y el token de dispositivo se introducen en Ajustes y no se versionan. El token compartido no adquiere vencimiento por estar solo en memoria: su revocación corresponde al backend. No se guardan claves reales, local.properties, APK, almacenes de firma ni productos de compilación en Git.

## Reanudar

1. Obtener la rama `fix/android-v021-control` del repositorio y abrir su carpeta `android/` en Android Studio, con JDK 17 y SDK 35.
2. Leer `android/README.md`; completar la configuración local Firebase si se probará «¿Qué ves?» desde el teléfono. Sin ella, sigue disponible la prueba local y el puente para el panel web.
3. Ejecutar `gradlew.bat testDebugUnitTest assembleDebug lintDebug` y luego instalar desde Android Studio en el teléfono físico.
4. Detener otros simuladores/puentes antes de activar el puente Android: comparten el mismo robot.
5. Registrar las pruebas físicas antes de dar por aprobada la integración y el movimiento.

## Verificación técnica realizada

Entorno aislado Linux, JDK Temurin 17, Gradle 8.13, SDK Android 35 y Build Tools 35.0.0. Comando: `gradle testDebugUnitTest assembleDebug lintDebug`. Resultado: **BUILD SUCCESSFUL**.

- 28 pruebas, 0 fallos y 0 errores (21 intérprete, 4 revocación de movimiento, 3 asociación de imágenes).
- APK debug generado sin datos Firebase ni token de dispositivo. Esto valida la construcción, no el inicio de sesión ni el funcionamiento físico.
- Lint: 0 errores y 8 advertencias. Quedan avisos de SDK/target más recientes, atributo de permiso ignorado en Android antiguo, orientación fija, reglas de extracción de datos, icono de aplicación y manejo específico de NoCredentialException. No se suprimieron los errores de lint.
- Se corrigieron los dos errores adicionales detectados al compilar: ACCESS_COARSE_LOCATION y uso del estado de AnimatedContent.
- SHA-256 del APK de esta ejecución: `3bcd2355bea11ae3e2936fbffa4b4a71f81a98e8444caf671f20b6322c5626d9`. El APK no se incorpora a Git y otra compilación puede producir otro hash.
