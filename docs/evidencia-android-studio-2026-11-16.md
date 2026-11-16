# Evidencia de ejecución en Android Studio: interfaz y fallo DNS de Cloud Run

**Fecha del registro:** 2026-11-16 (America/Lima)  
**Proyecto:** LLMia Android Bridge (Kotlin)  
**Entorno:** Android Studio, emulador Android; no dispositivo Android físico  
**Decisión vigente:** [ADR-02: Android como puente, sin reprogramar el firmware de la ESP32-CAM](ADR-02-android-bridge.md).

## Evidencia observada en las capturas compartidas por el operador

### E-AND-01 — Interfaz visible en emulador

Se observa la aplicación `LLMia · Android Bridge` abierta en un emulador Android. La interfaz contiene las secciones:

1. `Conectar Carrito_CAM (Wi-Fi local)`, SSID prellenado `Carrito_CAM` y control `CONECTAR AL ROBOT`.
2. `CAPTURAR FOTO REAL`, deshabilitado mientras no hay conexión con el robot.
3. `Gemini vía datos móviles`, acción `COMPROBAR CLOUD RUN POR DATOS MÓVILES`, campo para token temporal y controles para iniciar/detener el puente.
4. Panel `Eventos` para mensajes de diagnóstico.

**Resultado:** la interfaz se representa y se ejecuta en el emulador. Esto **no** acredita conexión Wi-Fi al robot, tráfico celular ni recepción de imágenes reales.

### E-AND-02 — Prueba de conectividad hacia Cloud Run

En la segunda captura, después de pulsar `COMPROBAR CLOUD RUN POR DATOS MÓVILES`, el panel de eventos indica:

```text
Eventos: listo para iniciar
Cloud Run inaccesible:
UnknownHostException: Unable to resolve host
"llmia-demo-913797829604.us-central1.run.app": No address
associated with hostname
```

**Observación:** la solicitud no puede resolver el hostname. No hay evidencia de un intercambio HTTP exitoso con Cloud Run ni de una llamada a Gemini desde la app Android. La indicación `Datos móviles: conexión validada` es texto mostrado por la interfaz y **no confirma** que el emulador cuente con una conexión celular física.

**Diagnóstico preliminar (hipótesis, no confirmado):** resolución DNS o selección de red en el emulador; podrían influir VPN, DNS de Windows o la implementación Android. No se ha investigado ni corregido aún.

## Pruebas pendientes — orden recomendado

- [ ] Dentro de Chrome **en el emulador**, verificar acceso a `https://www.google.com`.
- [ ] Dentro de Chrome **en el emulador**, verificar `https://llmia-demo-913797829604.us-central1.run.app/health`.
- [ ] Clasificar si el problema afecta todo Internet, solo el dominio de Cloud Run o solo a la app Kotlin.
- [ ] Revisar `ConnectivityManager` y selección de `Network` en Kotlin, únicamente si Chrome sí funciona.
- [ ] En equipo Android físico, comprobar Wi-Fi local `Carrito_CAM` y acceso simultáneo a Internet mediante datos móviles.
- [ ] Capturar un JPEG real desde `http://192.168.4.1/capture` dentro de Android.
- [ ] Validar integración autenticada con Cloud Run y respuesta de Gemini sobre una fotografía real, sin incrustar tokens en el código.

## Trazabilidad y limitaciones

El usuario compartió dos capturas en la conversación: vista de la app en Android Studio y vista del panel de eventos con `UnknownHostException`. Este archivo **transcribe la evidencia visual aportada**; los archivos de imagen originales no se han incorporado como binarios en este commit. No se ejecutó depuración, recompilación, cambio de firmware ni despliegue de nube como parte de este registro.

La prueba de visión del backend con **imágenes simuladas** está documentada por separado en [progreso de integración](progreso-integracion-2026-11-10.md).
