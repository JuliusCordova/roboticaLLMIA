# Despliegue de LLMia en un proyecto dedicado

Este corte prepara un solo contenedor con React y FastAPI para Cloud Run. El robot y el descriptor siguen siendo simulados. El adaptador escribe metadatos en la base Firestore `llmia-demo` y guarda los JPEG en Cloud Storage. La aplicación web y Firebase Auth deben pertenecer al mismo proyecto dedicado a LLMia. El proyecto de SmartRoute, `proyectopersonal-480420`, está bloqueado en el inventario y en el backend.

## 1. Inventario de solo lectura

Desde Cloud Shell en el repositorio:

```bash
export LLMIA_PROJECT='ID_REAL_DEL_PROYECTO_LLMIA'
export LLMIA_REGION='us-central1'
bash infra/preflight.sh "$LLMIA_PROJECT" "$LLMIA_REGION"
```

Verifica proyecto, facturación y región antes de crear recursos. No cambies el proyecto predeterminado de la sesión. Usa `--project="$LLMIA_PROJECT"` en cada operación.

## 2. Recursos y permisos

En el proyecto de LLMia:

- Registra una aplicación web en Firebase Auth, habilita Google, identifica el UID del operador y autoriza el host de Cloud Run una vez que lo conozcas.
- Habilita las APIs necesarias para Cloud Run, Artifact Registry, Cloud Build, Firestore, Cloud Storage, Secret Manager y Firebase Authentication. Crea un repositorio Docker de Artifact Registry llamado `llmia` en la región elegida. La identidad que ejecuta Cloud Build necesita permiso para publicar allí.
- Crea una cuenta de servicio de ejecución dedicada, por ejemplo `llmia-runtime`. Dale acceso a Firestore en el proyecto dedicado, permiso para consultar usuarios de Firebase Auth para verificar revocación, permiso de objetos limitado al bucket de LLMia y permiso para leer solo el secreto de dispositivo. No compartas identidades con SmartRoute. El operador de despliegue debe tener permiso para usar esa cuenta.
- Crea en Secret Manager `llmia-device-token` con una cadena aleatoria de al menos 24 caracteres ASCII sin espacios. Guarda la versión activa para el despliegue y la copia local del simulador. No pongas el token en la configuración web, YAML, Git ni argumentos de build.

Si la base nombrada o el bucket aún no existen, créalos en el proyecto dedicado, después de confirmar los resultados del inventario:

```bash
gcloud firestore databases create --project="$LLMIA_PROJECT" \
  --database=llmia-demo --location="$LLMIA_REGION" \
  --type=firestore-native --delete-protection

gcloud storage buckets create "gs://${LLMIA_PROJECT}-llmia-images" \
  --project="$LLMIA_PROJECT" --location="$LLMIA_REGION" \
  --uniform-bucket-level-access --public-access-prevention \
  --lifecycle-file=infra/storage-lifecycle.json --soft-delete-duration=0
```

La política del bucket elimina capturas elegibles después de un día; la ejecución de esa política es asíncrona. El backend conserva como máximo las 16 solicitudes más recientes por robot. Los JPEG se sirven únicamente por la API autenticada. Si un JPEG ya expiró, la API responde 404 al intentar recuperarlo.

## 3. Construir y desplegar

Exporta `LLMIA_WEB_KEY`, `LLMIA_AUTH_DOMAIN` y `LLMIA_WEB_APP_ID` con la configuración pública de la aplicación web Firebase. Copia `infra/run.env.example.yaml` a `infra/run.env.local.yaml` y reemplaza proyecto, bucket y UID. El archivo local se excluye de Git y del envío a Cloud Build. Exporta `LLMIA_DEVICE_SECRET_VERSION` con la versión numérica del secreto.

```bash
LLMIA_TAG="$(git rev-parse --short HEAD)"
LLMIA_IMAGE="${LLMIA_REGION}-docker.pkg.dev/${LLMIA_PROJECT}/llmia/web:${LLMIA_TAG}"

gcloud builds submit --project="$LLMIA_PROJECT" --config=cloudbuild.yaml \
  --substitutions="_IMAGE=$LLMIA_IMAGE,_FIREBASE_API_KEY=$LLMIA_WEB_KEY,_FIREBASE_AUTH_DOMAIN=$LLMIA_AUTH_DOMAIN,_FIREBASE_APP_ID=$LLMIA_WEB_APP_ID"

gcloud run deploy llmia-demo --project="$LLMIA_PROJECT" --region="$LLMIA_REGION" \
  --image="$LLMIA_IMAGE" --service-account="llmia-runtime@${LLMIA_PROJECT}.iam.gserviceaccount.com" \
  --env-vars-file=infra/run.env.local.yaml \
  --set-secrets="LLMIA_DEVICE_TOKEN=llmia-device-token:$LLMIA_DEVICE_SECRET_VERSION" \
  --min-instances=0 --max-instances=2 --timeout=45s --allow-unauthenticated
```

La URL permite abrir la pantalla de inicio. Las rutas de operador exigen Firebase y un UID permitido; las rutas de dispositivo exigen su credencial separada. No actives este acceso si una política de tu organización impide la invocación pública de Cloud Run. Ante una regresión, vuelve a la revisión anterior que pasó la prueba de humo desde Cloud Run.

## 4. Verificar el despliegue real

1. Comprueba `GET /health` y la pantalla inicial desde la URL HTTPS. Sin token, `GET /api/v1/robots/llmia-01/status` debe responder 401. Con una cuenta Firebase válida fuera de la lista, debe responder 403.
2. Desde `backend/`, instala las dependencias de desarrollo y ejecuta el simulador con `LLMIA_API_URL` apuntando a la URL de Cloud Run y `LLMIA_DEVICE_TOKEN` configurado en tu sesión: `python -m llmia.simulator --listen`. Detén el proceso al terminar para evitar tráfico y escrituras innecesarios.
3. Inicia sesión como operador autorizado. Pide dos capturas, comprueba que las dos fotos son distintas y que cada descripción corresponde a su foto. El resultado debe indicar simulación.
4. Verifica en consola que existen metadatos en Firestore `llmia-demo`, colección `llmia_robots`, y JPEG en el bucket privado. Confirma que una petición sin token no puede ver la foto. Verifica que la consulta de una observación previa funciona después de cambiar a una nueva revisión con la misma configuración de almacenamiento.
5. Registra URL, revisión, commit, fecha y resultados con el simulador antes de integrar Gemini y el firmware.

**Estado de este corte:** pruebas de adaptadores con dobles locales y frontend/API verificados localmente. La imagen de contenedor, las credenciales reales y la persistencia en los servicios gestionados requieren comprobación en el proyecto dedicado.
