# Punto de control: demo funcional y mejoras diferidas

Fecha: 20 de noviembre de 2026 (Lima). Decisión de Julius: detener los cambios y presentar lo que ya funciona; las mejoras quedan para una fase posterior.

## Base de la demostración

- Código de referencia Android/backend: commit `4712f603e66c8cbe5facd680b4b519a11df7028a`, preservado en la rama [demo/v0.3](https://github.com/JuliusCordova/roboticaLLMIA/tree/demo/v0.3).
- Julius reportó funcionando en Android físico: control local del carrito, cámara, distancia frontal, inicio de sesión con Google, puente con sesión Firebase sin copiar token y consultas visuales por texto/voz con descripción de Gemini.
- Último despliegue confirmado por salida de Cloud Shell: `llmia-demo-00004-75j`, con 100 % del tráfico, y `/health` en modo Gemini. Imagen de esa revisión: `sha256:771a7db57fc51468b03cb81c2aed14bea3d35bd79192f7adfcbbb71bb98af1eb`.
- Servicio: https://llmia-demo-913797829604.us-central1.run.app
- El problema de datos móviles del teléfono se resolvió cambiando de SIM.

Las funciones anteriores son avances reportados por el operador; no equivalen a una certificación de todos los criterios de aceptación ni de las latencias físicas de parada.

## Alcance para presentar

Usar la app que ya funciona y preguntas sobre la foto actual: «¿Qué ves?», «¿Hay una botella?» o «¿De qué color es esa botella?». El intérprete local conserva sus órdenes cortas y STOP; Gemini describe imágenes y no acciona las ruedas. Mantener supervisión del prototipo.

No presentar conversación con memoria, búsqueda automática con servo, seguimiento ni desplazamiento hacia un animal como funciones ya demostradas.

## Fase posterior: v0.4

El código de contexto y búsqueda con cámara se conserva en `fix/android-v021-control`, [PR #5](https://github.com/JuliusCordova/roboticaLLMIA/pull/5). Tiene 44 pruebas de backend y 44 JVM de Android, además de compilación, lint y aislamiento debug/release verificados sobre el código registrado en su [punto de control](https://github.com/JuliusCordova/roboticaLLMIA/blob/fix/android-v021-control/docs/PUNTO_DE_CONTROL_2026-11-19_CONTEXTO_BUSQUEDA.md).

Su aceptación física y su despliegue efectivo no quedaron confirmados. La imagen v0.4 sí fue construida y publicada:
`us-central1-docker.pkg.dev/proyectopersonal-480420/llmia/web@sha256:a41e2d04d3c1d4c177e39e41ae85faa9c2036ecdc300fb6c3ffa3d39de0e79b7`.

Cloud Shell falló al renovar credenciales («service account info is missing 'email' field»). El intento de login mostró `redirect_uri_mismatch`. Se abrió la configuración web de Cloud Run y se observó el prefijo de la imagen nueva, pero no hubo evidencia final de revisión activa ni de distribución de tráfico. Por tanto, el estado actual de ejecución debe comprobarse antes de afirmar qué imagen sirve la URL. No asumir ni un despliegue completado ni un rollback.

No se modifican ahora servicios, firmware, IAM, secretos ni instalación Android. No reconstruir la imagen por este corte de trabajo y no habilitar Cloud Testing/Tool Results para retomarlo.

## Retomar en la siguiente sesión

1. Confirmar en Cloud Run qué revisión, digest y tráfico están activos; contrastar con la app instalada.
2. Mantener la base 0.3 para la demo. Cualquier cambio de imagen o rollback debe ser una acción explícita y comprobada; este registro no lo ejecuta.
3. Resolver la autorización de Cloud Shell solo si hace falta para el siguiente despliegue.
4. En otra fase, desplegar e instalar v0.4 y validar contexto, nueva conversación, búsqueda con servo, ausencia, incertidumbre y cancelación usando primero un objeto inmóvil.
5. La navegación o aproximación a objetivos es una fase adicional, no implementada ni aprobada físicamente.

El código de 0.4 se conserva; no se integra a main como parte de este cierre. La carpeta local `D:\Desarrollo\roboticaLLMIA-v03` y su `local.properties` no se modifican desde aquí.
