# Punto de control: sesión del puente y preguntas visuales

Fecha: 19 de noviembre de 2026, Lima. Incremento Android 0.3.0, sobre la rama `fix/android-v021-control` (PR #5). Código publicado no equivale a Cloud Run desplegado ni APK instalado.

## Evidencia reportada por Julius

- El Android físico muestra cámara del carrito y distancia frontal.
- El fallo de Internet por datos móviles se resolvió cambiando de SIM.
- Cloud Run/Gemini y el puente funcionan con el token manual anterior.
- No se da por demostrada la búsqueda visual dirigida ni el nuevo puente Firebase en hardware.

## Implementación del incremento

- Android obtiene el ID token Firebase antes de cada poll y upload. El SDK administra renovación. No recibe ni guarda el secreto compartido del dispositivo.
- Las rutas nuevas de puente verifican al operador usando el verificador existente (revocación y UID permitido). Poll y upload están limitados al robot aprobado y al propietario de la observación. La autorización de propiedad del upload también se verifica en la transición atómica del núcleo.
- Otro compañero puede entrar desde otro teléfono con su Google. Debe existir su usuario Firebase y el administrador debe agregar su UID a `LLMIA_ALLOWED_UIDS`, preservando los anteriores. No se entrega a compañeros acceso a Cloud Shell, Secret Manager ni IAM.
- El puente se apaga al cerrar sesión o pasar la app a segundo plano. Al regresar se activa manualmente, sin copiar credenciales.
- Pregunta opcional de 1 a 500 caracteres en POST observations, persistida con el ID. Clientes sin cuerpo siguen usando «¿Qué ves?». Validación estricta y sin campos adicionales.
- Foto nueva por solicitud, asociación estricta por ID, vencimiento y protección contra inferencia duplicada conservados.
- Prompt corregido: foto del entorno desde la cámara; no se presupone un robot visible. Pregunta separada de las instrucciones de sistema. Buscar analiza solo el encuadre; si no aparece el objeto debe expresarlo. No hay exploración autónoma ni herramientas para motores.
- Rutas `/device/v1` y secreto del simulador permanecen compatibles.

## Pruebas

Backend: 35 pruebas locales pasaron, incluyendo cuenta de compañero, 401/403, aislamiento entre propietarios, robot incorrecto, cuerpo inválido, pregunta transmitida con JPEG y compatibilidad antigua y lectura de registros Firestore sin pregunta. Las 38 pruebas JVM de Android pasaron localmente, incluidas consultas visuales y prioridad de STOP. GitHub Actions ejecutó con éxito `testDebugUnitTest assembleDebug lintDebug`, repitió con el modo de red debug habilitado y comprobó que release fuerza ese modo a `false`. No se usa Firebase Test Lab ni dispositivos de nube.

Código validado: `59d9f49d9f9bd91f31e5b53d422958287c7164de`.

- [Android: compilación, APK, pruebas, lint y aislamiento de red](https://github.com/JuliusCordova/roboticaLLMIA/actions/runs/44662947720): SUCCESS.
- [Backend](https://github.com/JuliusCordova/roboticaLLMIA/actions/runs/44662947647): SUCCESS.
- Otra ejecución Android falló al resolver dependencias Maven antes de compilar. Se reintentó; la ejecución exitosa anterior valida el mismo código. No se cambian versiones ni repositorios para ocultar ese fallo.
- La compilación completa local también quedó bloqueada al resolver el plugin Android; la evidencia de APK/lint corresponde a GitHub Actions. No se entrega un APK local configurado con Firebase.

## Orden de instalación y aceptación pendiente

1. Construir imagen desde esta rama con `python3 infra/build_from_firebase.py` y actualizar solamente `llmia-demo` con esa imagen, conservando variables, cuenta y secretos actuales. Ver `infra/README.md`.
2. Sin sesión, las nuevas rutas de puente deben responder 401. Un usuario Firebase no permitido debe recibir 403. `/health` confirma modo; no prueba inferencia.
3. Instalar Android 0.3.0 desde la misma rama, manteniendo `local.properties` local y registrando SHA-1 del certificado que realmente firma el APK en Firebase.
4. Entrar con cuenta autorizada, comprobar Cloud Run e iniciar puente sin campo de token. Apagar simuladores anteriores para evitar reclamos simultáneos.
5. Con un objeto visible, pedir su descripción por texto y por voz. Retirarlo y repetir: otra captura/ID y respuesta de ausencia. No usar personas como objeto de validación.
6. Repetir con un compañero autorizado; verificar denegación antes de incorporar su UID. El panel web y el puente deben usar la misma cuenta para esa consulta.
7. Cerrar sesión: no se aceptan nuevas consultas desde ese puente. Comprobar segundo plano, vencimiento y parada física por separado.

No modifica firmware, IAM, reglas Firebase ni recursos de SmartRoute. No se han desplegado servicios desde este entorno.
