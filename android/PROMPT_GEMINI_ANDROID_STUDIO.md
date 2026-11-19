> Referencia histórica de v0.2. Para la versión corregida, consultar README.md y docs/ARQUITECTURA.md.

# Prompt opcional para Gemini dentro de Android Studio

Actúa como Android Staff Engineer y revisa este proyecto Android Studio `LLMiaAndroid`, cuyo objetivo es hacer de puente entre ESP32-CAM (firmware local ya operativo en AP `Carrito_CAM`) y el backend existente en Cloud Run.

**Contratos existentes e inmutables**:
- GET `http://192.168.4.1/capture` JPEG local desde red Wi-Fi Carrito_CAM.
- GET `https://llmia-demo-913797829604.us-central1.run.app/health` por Internet.
- POST `/device/v1/poll` (Bearer token del dispositivo) devuelve 204 cuando no hay trabajo o JSON con `id` de captura.
- PUT `/device/v1/observations/{id}/capture` con `Content-Type: image/jpeg` y el mismo Bearer token. Máximo 512 KiB.
- El backend ya envía JPEG a Gemini y el panel web presenta los resultados. No llamar a Gemini directamente desde Android.

**Restricciones**:
- NO cambiar el firmware ESP32-CAM ni los endpoints backend existentes; NO almacenar secretos en fuente, SharedPreferences, repositorio o logs.
- Los motores se controlan SOLO por la red local (`/move` del firmware), con el intérprete local `CommandParser`; Gemini nunca decide movimientos. Mantener la repetición cada 200 ms, el STOP final y los topes de duración.
- Mantén acceso dual Wi-Fi local y red celular: `WifiNetworkSpecifier` + `ConnectivityManager` y `Network.openConnection` por interfaz; nunca `bindProcessToNetwork` para todo el proceso.
- Seguridad: HTTP claro solo hacia 192.168.4.1 y HTTPS TLS para Cloud Run; validar JPEG y 512 KiB; gestionar 401/403, desconexiones y cancelación.
- Evitar introducir dependencia de API keys de Gemini en APK. La credencial compartida actual es para demo supervisada y debe reemplazarse para distribución.

**Tarea**: revisa compilación, permisos Android por API 29–35, manejo de callbacks, recursos/lifecycle y asincronía. Corrige errores demostrables, genera pruebas unitarias e instrumentadas, documenta los cambios y valida primera captura local en un Android real. Propón plan de autenticación por dispositivo antes de producción. Trabaja incrementalmente y conserva compatibilidad con contrato LLMia.
