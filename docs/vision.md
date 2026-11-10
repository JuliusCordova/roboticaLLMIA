# Validar la visión de LLMia con Gemini

Activa Gemini solo después de validar Cloud Run, Firebase, Firestore y Cloud Storage con la simulación. Mientras tanto, `LLMIA_VISION_MODE=simulator` evita llamadas a Vertex AI.

1. En el proyecto `proyectopersonal-480420`, revisa si Vertex AI ya está habilitado. Para la prueba usa la cuenta de ejecución `llmia-runtime` y concede el permiso de inferencia necesario (`aiplatform.endpoints.predict`), con el alcance mínimo acordado. La aplicación usa credenciales administradas; no requiere claves en el firmware ni en React.
2. En el archivo local de entorno de Cloud Run, configura:

   ```yaml
   LLMIA_VISION_MODE: "gemini"
   LLMIA_GEMINI_MODEL: "gemini-3.5-flash"
   LLMIA_GEMINI_LOCATION: "global"
   ```

   Sigue el [runbook de despliegue](../infra/README.md). La ruta `/health` debe indicar `"mode":"gemini"`; esto confirma la selección del adaptador, no la conectividad con Vertex AI.
3. Con la placa real y un operador autorizado, solicita una observación por escena. Confirma que la imagen publicada es la que se usó para la descripción. Registra ID de observación, revisión de Cloud Run, commit, tiempo y coincidencia. Usa objetos sin personas ni datos personales.

| Escena | Objeto esperado | Resultado |
| --- | --- | --- |
| V1 | Botella | Pendiente |
| V2 | Cuaderno | Pendiente |
| V3 | Taza | Pendiente |
| V4 | Caja | Pendiente |
| V5 | Pelota | Pendiente |

Aceptación: al menos 4 de 5 objetos correctamente descritos en español. Mide el plazo de 30 s desde la solicitud hasta el resultado. Si Vertex AI falla, se agota el plazo o la respuesta no es utilizable, la observación termina en error o vencida. La aplicación no muestra una descripción simulada como sustituto silencioso.

El backend envía el JPEG de la observación a Vertex AI como datos de la solicitud. El adaptador no tiene herramientas ni control sobre motores. Evita guardar el contenido de la imagen o de la respuesta en logs de ejecución.

- [Comprensión de imágenes](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/capabilities/image-understanding)
- [Gemini 3.5 Flash](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/guides/gemini-3-5-flash)
