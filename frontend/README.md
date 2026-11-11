# Panel web de LLMia

Primera interfaz de la FAST DEMO. Muestra la conexión del robot, permite pedir «¿Qué ves?» y presenta la foto y la descripción de la misma observación. Usa el simulador hasta integrar el firmware real.

## Prueba local sin un proyecto Firebase

1. Ejecuta el backend en modo local según [sus instrucciones](../backend/README.md).
2. En otra terminal con el mismo `LLMIA_DEVICE_TOKEN`, desde `backend/`, ejecuta `python -m llmia.simulator --listen`. El simulador alterna dos JPEG de colores.
3. Desde `frontend/`, con Node 22.12 o superior:

   ```bash
   npm ci
   VITE_LOCAL_DEMO=true npm run dev
   ```

4. Abre la dirección que imprime Vite. Introduce el `LLMIA_OPERATOR_TOKEN` en el formulario local y pulsa **¿Qué ves?**. La opción del token local solo existe en desarrollo y el navegador no la persiste.

## Probar Firebase real

1. En un proyecto Firebase de pruebas, registra la aplicación web, habilita Google como proveedor y autoriza el dominio donde ejecutarás el panel. Para desarrollo autoriza `localhost` si aún no figura.
2. Copia `.env.example` a `.env.local` y completa la configuración pública de la app. No agregues claves de cuenta de servicio o tokens del robot al frontend.
3. Identifica en Firebase Auth el UID de cada cuenta operadora. Instala `backend/requirements-firebase.txt` además de las dependencias normales y configura el backend con `LLMIA_AUTH_MODE=firebase`, `LLMIA_FIREBASE_PROJECT_ID`, `LLMIA_ALLOWED_UIDS` (separados por coma) y `LLMIA_DEVICE_TOKEN`. Usa Application Default Credentials con acceso al proyecto y permiso para consultar usuarios de Firebase Auth, necesario para verificar revocación. Las cuentas no autorizadas pueden iniciar sesión en Firebase, pero la API responderá 403.
4. Ejecuta Vite sin `VITE_LOCAL_DEMO`, inicia sesión con Google y prueba la captura. Para compilar el frontend, usa `npm run build`.

El panel y FastAPI se verifican juntos por el proxy de Vite. Para publicar el frontend y la API en un único servicio HTTPS con Firestore y Cloud Storage, sigue el [runbook de Cloud Run](../infra/README.md). El servicio real de Cloud Run fue desplegado y el panel devolvió una descripción de Gemini para una captura del simulador. Todavía falta validar todo el conjunto de criterios de aceptación y la ESP32-CAM física. Ver [bitácora](../docs/progreso-integracion-2026-11-10.md).
