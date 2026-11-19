# Android v0.2.1 — correcciones de la revisión del 19 de noviembre de 2026

Fuente: ZIP `LLMiaAndroid_v0.2 (2).zip` entregado por el operador. Base de comparación: `main` f2a519c67f470f6f8d3734661b56913c205f28a3. La app se incorpora bajo `android/`; mantiene la arquitectura de ADR-02 y las rutas del firmware y backend existentes. No cambia firmware, IAM, secretos ni despliegue Cloud Run.

## Correcciones

- Permiso CHANGE_NETWORK_STATE para solicitar el Wi-Fi local.
- Gramática explícita de movimiento: rechaza negaciones, preguntas, alternativas, secuencias y tiempos no positivos. STOP tiene prioridad salvo la orden explícita para detener el barrido.
- MOVE y STOP pasan por la misma cola HTTP. Una autorización de movimiento revocable evita enviar órdenes antiguas todavía en cola. Las transiciones esperan la terminación anterior, incluida su limpieza, antes de empezar otra maniobra.
- Las capturas, el sensor y el servo no reservan la red durante el movimiento. Una captura que ya empezó puede retrasar el inicio del movimiento. No se promete STOP instantáneo ni temporización física exacta.
- Las flechas aplican los topes por dirección de las órdenes de texto; soltar/cancelar el gesto solicita STOP en finally.
- Salir de la actividad cancela el reconocimiento de voz e invalida sus callbacks, bloquea nuevas órdenes, solicita STOP y apaga el puente. Volver requiere activar el puente de nuevo.
- Fotos indexadas por ID con retención acotada. Un error o vencimiento sin foto no reutiliza otra captura. Se comprueba que el resultado pertenece al ID solicitado.
- Reinicio del puente serializado con cancelAndJoin. Se valida CAPTURE, formato del ID y vencimiento antes de capturar/enviar. Se mantienen los destinos aprobados de esta demo.
- Ejemplo local.properties sin comentarios al final de los valores, versión 0.2.1 y documentación con límites reales del cliente y de la voz.

## Pruebas automatizadas

28 pruebas JVM: 21 del intérprete, 4 de autorización de movimientos y 3 de asociación/retención de imágenes. Incluyen los casos que fallaban en la revisión: «no gires a la izquierda», «izquierda o derecha», «avanza y luego retrocede», «avanza -2 segundos» y «avanza 0 segundos».

El workflow `.github/workflows/android.yml` ejecuta pruebas, compilación debug y lint para cambios en la app. No requiere credenciales Firebase, token de dispositivo ni acceso al robot. El APK sin configuración Firebase deja inhabilitado el inicio de sesión de operador.

## Pendiente en hardware

1. Motores desconectados: conectar Carrito_CAM y verificar cámara, distancia y red celular simultánea en Android físico.
2. Ruedas elevadas: MOVE/STOP, límite por dirección, soltar/cancelar gesto, cambiar pantalla, salir de la app y resultado de voz tardío.
3. Cámara lenta o enlace cortado: verificar que no se envía un MOVE revocado después de STOP y medir la parada real, sin asumir 700 ms exactos.
4. Conexión completa: pedir dos fotos diferentes, comprobar mismo ID/foto/resultado y probar vencimiento sin reutilizar imagen anterior. Detener otros simuladores/puentes.
5. Circuito supervisado: obstáculo apareciendo durante el avance, pérdida de Wi-Fi y las cinco escenas de aceptación de la SPEC.

Las pruebas automatizadas no certifican la parada del montaje real. El firmware comprueba el watchdog desde loop(), después de atender HTTP; validar o mejorar esa protección local constituye un trabajo separado.

## Verificación técnica realizada

Entorno aislado Linux, JDK Temurin 17, Gradle 8.13, SDK Android 35 y Build Tools 35.0.0. Comando: `gradle testDebugUnitTest assembleDebug lintDebug`. Resultado: **BUILD SUCCESSFUL**.

- 28 pruebas, 0 fallos y 0 errores (21 intérprete, 4 revocación de movimiento, 3 asociación de imágenes).
- APK debug generado sin datos Firebase ni token de dispositivo. Esto valida la construcción, no el inicio de sesión ni el funcionamiento físico.
- Lint: 0 errores y 8 advertencias. Quedan avisos de SDK/target más recientes, atributo de permiso ignorado en Android antiguo, orientación fija, reglas de extracción de datos, icono de aplicación y manejo específico de NoCredentialException. No se suprimieron los errores de lint.
- Se corrigieron los dos errores adicionales detectados al compilar: ACCESS_COARSE_LOCATION y uso del estado de AnimatedContent.
- SHA-256 del APK de esta ejecución: `3bcd2355bea11ae3e2936fbffa4b4a71f81a98e8444caf671f20b6322c5626d9`. El APK no se incorpora a Git y otra compilación puede producir otro hash.
