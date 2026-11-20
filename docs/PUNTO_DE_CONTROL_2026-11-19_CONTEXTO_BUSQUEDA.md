# Punto de control: contexto y búsqueda solo con cámara (Android 0.4)

> **En pausa — fase posterior (20-11-2026):** Julius decidió presentar la demo 0.3 reportada funcionando, preservada en [demo/v0.3](https://github.com/JuliusCordova/roboticaLLMIA/tree/demo/v0.3). Este código 0.4 conserva las mejoras para otra fase; su aceptación física y su despliegue efectivo no quedaron confirmados. Ver [cierre de demo](https://github.com/JuliusCordova/roboticaLLMIA/blob/main/docs/PUNTO_DE_CONTROL_2026-11-20_CIERRE_DEMO.md).

Fecha de referencia: 19-11-2026, Lima. Rama `fix/android-v021-control`, PR #5. No se modifica el firmware.

## Base reportada por Julius

Julius confirmó que la versión 0.3 funciona en Android físico y con el backend desplegado como `llmia-demo-00004-75j`. La imagen desplegada fue `sha256:771a7db57fc51468b03cb81c2aed14bea3d35bd79192f7adfcbbb71bb98af1eb`. Esta evidencia corresponde a 0.3: no demuestra todavía las funciones 0.4.

## Contrato y contexto

POST observations admite `parent_id` opcional (ID de una respuesta LISTA del mismo propietario y robot) y `target` opcional (1–80 caracteres). El servidor construye como máximo tres pares pregunta/respuesta desde sus propios registros, sin aceptar un historial enviado por el cliente. Cada observación conserva su snapshot de contexto dentro del agregado existente, limitado a 16 observaciones. No se crea otro almacén ni se usan imágenes anteriores como prueba de la escena actual.

Cada consulta obtiene una foto nueva. Gemini recibe el historial como datos y debe distinguir lo anterior de lo que es visible ahora. Una referencia ajena o eliminada responde 404; una referencia sin respuesta válida responde 409. Registros antiguos sin los campos nuevos siguen siendo compatibles.

Android mantiene solo el ID de la última respuesta válida. «Nueva conversación», el botón equivalente y los cambios de cuenta reinician ese cursor y descartan resultados tardíos de la conversación anterior. Reiniciar contexto no borra los registros del servidor ni sustituye la política de retención existente.

## Búsqueda finita sin ruedas

«Busca un perro y descríbelo» y «Encuentra una botella con la cámara» se interpretan como búsqueda solo con servo. Órdenes compuestas como «busca un perro y avanza» se rechazan. Las preguntas de seguimiento («¿Y de qué color es?») usan contexto y una foto nueva sin volver a barrer.

Secuencia determinista Android: revocar movimiento previo → esperar limpieza → exigir STOP confirmado → suspender refresco de cámara/sensor → ángulos solicitados 90°, 30°, 150°. La ruta `/servo` existente desactiva el barrido automático. Se valida `SERVO OK`, se esperan 3,2 s (firmware: pasos de 15 ms hasta 180°) y se descartan dos capturas para vaciar buffers anteriores antes de crear la observación. El firmware no confirma posición física; este margen y los ángulos deben validarse en el montaje.

Gemini devuelve JSON restringido y validado: `match` = FOUND / NOT_FOUND / UNCERTAIN y `description`. No se decide el hallazgo buscando palabras en la descripción. Un FOUND termina la búsqueda; tres negativos solo significan ausencia en esas vistas; cualquier incertidumbre impide afirmar ausencia completa. No existen herramientas de movimiento en Gemini.

Máximo tres inferencias por búsqueda, una por ángulo, con el plazo de observación existente y un timeout global objetivo de 120 s. La cancelación de una petición HTTP ya en curso depende de su timeout; STOP no deshace una inferencia ya aceptada. No hay bucle indefinido ni seguimiento de animales. El modo simulador devuelve UNCERTAIN para objetivos.

Durante la búsqueda se bloquean movimientos manuales, servo manual, barrido y nuevas consultas; STOP cancela y solicita parada. Pasar a segundo plano, perder el Wi-Fi, detener el puente o cerrar sesión cancela la tarea. El servo que ya recibió un objetivo puede terminar ese movimiento; no se envían nuevos ángulos después de cancelar. El refresco se reactiva al terminar si la app sigue visible y conectada. Usar un solo controlador del robot durante esta prueba: no se bloquean otros clientes locales de la ESP32.

## Verificación y aceptación

Pruebas automatizadas: contexto acotado, foto nueva, autorización de propietarios, historial no inyectable, persistencia Firestore compatible, clasificación estructurada, límite de tres vistas, detención temprana, STOP fallido y cancelación durante espera. El resultado de CI se registra en el PR.

Pendiente en físico tras desplegar backend e instalar Android 0.4:

1. Con el carrito inmóvil y supervisado, «¿Qué ves?» seguido de «¿Y de qué color es?»; mover o retirar el objeto para comprobar que no se presenta contexto viejo como evidencia actual.
2. «Nueva conversación»: comprobar que pide aclaración si una referencia es ambigua y no tiene antecedente claro.
3. Colocar un objeto inmóvil a izquierda/derecha. «Busca una botella»: verificar ángulos reales, foto de cada vista, parada al encontrarlo y que las ruedas no reciben órdenes de avance/giro.
4. Objeto ausente y escena oscura: distinguir ausencia de incertidumbre, máximo tres consultas.
5. STOP durante asentamiento, consulta y subida; segundo plano, pérdida de Wi-Fi, sesión cerrada, fallo del servo y del STOP. Medir latencias reales.
6. Repetir con otra cuenta autorizada sin mezclar contextos. No probar acercamientos a animales ni navegación autónoma.

Primero desplegar el backend nuevo, luego instalar Android. No se han modificado Cloud Run, IAM, Firebase, Storage, secretos ni firmware desde este entorno. No se usa Firebase Test Lab.

Referencia del contrato de salida estructurada: https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/capabilities/control-generated-output

## Resultado automatizado final

Código validado: `bdbb2ecfb2e961171889f9eaee4468e76bb8a1a7`.

- Backend: **44 pruebas locales OK** y [GitHub Actions SUCCESS](https://github.com/JuliusCordova/roboticaLLMIA/actions/runs/44677360137).
- Android: **44 pruebas JVM locales OK**. [GitHub Actions SUCCESS](https://github.com/JuliusCordova/roboticaLLMIA/actions/runs/44677360136) con `testDebugUnitTest assembleDebug lintDebug`; segunda ejecución con Internet debug habilitado y comprobación de `ALLOW_DEFAULT_CLOUD_NETWORK=false` en release.
- Se corrigió la reanudación de vista previa al cancelar durante el apagado de sensores. Una salida por error del puente también cancela las tareas visuales. El prompt pide aclaración ante referencias sin contexto claro.
- El commit posterior solo actualiza README y este registro; no cambia código. No se han ejecutado estas funciones 0.4 sobre el montaje físico ni se ha desplegado su backend desde este entorno.
