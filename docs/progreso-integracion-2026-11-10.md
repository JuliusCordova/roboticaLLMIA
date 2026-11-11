# Bitácora de integración · 10 de noviembre de 2026

## Alcance y origen de evidencias
Registro de resultados observados en la sesión de Cloud Shell y confirmados por el operador. Se refiere al despliegue real del backend y a **imágenes generadas por el simulador**, no a imágenes procedentes de la ESP32-CAM física.

## Evidencias confirmadas
| Componente | Evidencia | Estado |
| --- | --- | --- |
| ESP32-CAM local | Operador confirmó que puede ver `http://192.168.4.1/capture` conectado a `Carrito_CAM` | Confirmado manualmente |
| Red de destino | `Wifi Spike`, 2.4 GHz, canal 9, WPA2-Personal, reportado por `netsh wlan show interfaces` | Compatible por características; **sin conexión de ESP32 verificada** |
| Simulador nube | Dos cargas de prueba completaron en `LISTA`, una roja y otra azul, desde `python -m llmia.simulator --listen` | Confirmado |
| Vertex AI API | `gcloud services list --enabled` devolvió `aiplatform.googleapis.com` | Confirmado |
| IAM | Rol personalizado `projects/proyectopersonal-480420/roles/llmiaVertexPredictor` creado con `aiplatform.endpoints.predict` y vinculado a `llmia-runtime` | Confirmado |
| Cloud Run | Servicio `llmia-demo` en `us-central1`; despliegue `llmia-demo-00003-ptg` con 100% de tráfico | Confirmado en salida de CLI |
| Gemini | Variables `LLMIA_VISION_MODE=gemini`, `LLMIA_GEMINI_MODEL=gemini-3.5-flash`, `LLMIA_GEMINI_LOCATION=global`; `GET /health` respondió `{"status":"ok","mode":"gemini"}` | Adaptador seleccionado |
| Inferencia real | Panel mostró una descripción de imagen azul del simulador generada en el flujo con Gemini, sin la etiqueta de simulación | Confirmado por observación del operador; no evalúa objetos reales |

Servicio: `https://llmia-demo-913797829604.us-central1.run.app`.

**Limitación:** la respuesta de salud sola no demuestra inferencia. El resultado del panel sí respalda una prueba de inferencia, pero no establece exactitud sobre objetos ni certifica toda la matriz de aceptación. Tampoco se ha vuelto a consultar el estado actual del despliegue desde esta bitácora.

## Próximos cortes (pendientes)
1. Compilar firmware AP+STA de rama `feature/esp32-wifi-sta` y cargarlo por USB con motores desconectados.
2. Verificar en monitor serie dos direcciones IP: AP `Carrito_CAM` y STA `Wifi Spike`; comprobar `/capture` en ambas.
3. Implementar **cliente HTTPS saliente** en ESP32-CAM con autenticación de dispositivo, polling y subida JPEG sin exponer token en GitHub.
4. Validar una captura real en Gemini, luego las cinco escenas (botella, cuaderno, taza, caja, pelota).
5. Registrar resultados de exactitud y latencia; probar voz por separado.

## Restricciones
- No modificar ni mezclar recursos de SmartRoute AI, `gestioncitas` o la base `(default)`.
- No poner contraseñas Wi-Fi, tokens del dispositivo o credenciales de servicio en el repositorio.
- No habilitar acciones remotas sobre motores en esta fase.
