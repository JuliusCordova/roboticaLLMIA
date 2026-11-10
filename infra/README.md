# Despliegue de LLMia en Google Cloud

LLMia comparte el proyecto `proyectopersonal-480420` con SmartRoute AI. Los recursos de ejecución y datos son propios de LLMia:

| Recurso | Identificador |
| --- | --- |
| Cloud Run | `llmia-demo` |
| Firestore Native | `llmia-demo` |
| Cloud Storage privado | `proyectopersonal-480420-llmia-images` |
| Cuenta de ejecución | `llmia-runtime@proyectopersonal-480420.iam.gserviceaccount.com` |
| Artifact Registry | `llmia` |
| Secret Manager | `llmia-device-token` |
| App web Firebase | `llmia-web` |

El backend fija el proyecto, la base y el bucket anteriores. Rechaza `(default)`, `smartroute`, `gestioncitas` y el bucket predeterminado. El contenedor sirve React y FastAPI. La placa se simula y Gemini permanece desactivado por defecto. El [adaptador de visión](../docs/vision.md) se habilita después de verificar el despliegue.

## 1. Inventario de solo lectura

Desde Cloud Shell en el repositorio:

```bash
export LLMIA_PROJECT='proyectopersonal-480420'
export LLMIA_REGION='us-central1'
bash infra/preflight.sh "$LLMIA_PROJECT" "$LLMIA_REGION"
```

El inventario no modifica recursos y muestra solo los de LLMia. Verifica proyecto, facturación y región antes de crear recursos. No cambies el proyecto predeterminado de la sesión. Usa `--project="$LLMIA_PROJECT"` en cada operación.

## 2. Recursos y permisos

En el proyecto compartido:

- Registra la app web `llmia-web` en Firebase. Las apps del mismo proyecto comparten usuarios, proveedores y dominios autorizados. Usa el proveedor Google ya existente, agrega únicamente el dominio de LLMia y no elimines configuraciones de otras apps. FastAPI autoriza solo los UID de LLMia definidos en `LLMIA_ALLOWED_UIDS`.
- Comprueba qué APIs ya están habilitadas; habilita solo las necesarias para Cloud Run, Artifact Registry, Cloud Build, Firestore, Cloud Storage, Secret Manager y Firebase Authentication. Crea un repositorio Docker de Artifact Registry llamado `llmia` en la región elegida. La identidad que ejecuta Cloud Build necesita permiso para publicar allí.
- Crea la cuenta de ejecución `llmia-runtime`. Concede `roles/storage.objectUser` solo sobre el bucket de LLMia, acceso solo al secreto de dispositivo y el permiso necesario para consultar usuarios de Firebase Auth al verificar revocación. Este último permiso corresponde al proyecto compartido. El operador de despliegue necesita permiso para usar la cuenta de ejecución.
- Crea en Secret Manager `llmia-device-token` con una cadena aleatoria de al menos 24 caracteres ASCII sin espacios. Guarda la versión activa para el despliegue y la copia local del simulador. No pongas el token en la configuración web, YAML, Git ni argumentos de build.

Si la base nombrada o el bucket aún no existen, créalos con los nombres exclusivos de LLMia, después de confirmar los resultados del inventario:

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

## 3. Comprobar el aislamiento antes de desplegar

Una vez creada la base, concede a la cuenta de ejecución `roles/datastore.user` **solo** con la condición del archivo versionado. No otorgues ese rol sin condición, ni roles amplios como Editor, a esta identidad.

```bash
gcloud projects add-iam-policy-binding "$LLMIA_PROJECT" \
  --member="serviceAccount:llmia-runtime@${LLMIA_PROJECT}.iam.gserviceaccount.com" \
  --role=roles/datastore.user \
  --condition-from-file=infra/firestore-iam-condition.yaml
```

Ejecuta la prueba de solo lectura con credenciales ADC de un operador autorizado a impersonar temporalmente a `llmia-runtime`. Instala primero `backend/requirements-cloud.txt` en tu entorno Python.

```bash
python infra/verify_firestore_isolation.py
```

El resultado debe permitir leer en `llmia-demo` y devolver permiso denegado para `(default)`, `smartroute` y `gestioncitas`. Usa una ruta de prueba sin datos de negocio. Si un acceso ajeno no se rechaza, revisa los permisos efectivos y no despliegues. Para mayor separación del padrón de usuarios de Firebase, haría falta otro proyecto de Firebase o una configuración explícita de inquilinos; la FAST DEMO usa una lista de operadores autorizados.

## 4. Construir y desplegar

La configuración de la app web se obtiene directamente de Firebase con tu cuenta de Cloud Shell, para evitar errores al copiar `apiKey` y `appId`. La validación comprueba el proyecto y el dominio autorizado antes de construir la imagen. No crea ni modifica la app de Firebase.

```bash
python3 infra/build_from_firebase.py
```

Una vez que el build termine en `SUCCESS`, toma la imagen publicada de su salida. Conserva `infra/run.env.local.yaml` con el UID autorizado y el resto de la configuración del servicio, y usa la versión numérica habilitada del secreto:

```bash
LLMIA_IMAGE='IMAGEN_O_DIGEST_DEL_BUILD_CORRECTO'
LLMIA_DEVICE_SECRET_VERSION=1

gcloud run deploy llmia-demo --project="$LLMIA_PROJECT" --region="$LLMIA_REGION" \
  --image="$LLMIA_IMAGE" --service-account="llmia-runtime@${LLMIA_PROJECT}.iam.gserviceaccount.com" \
  --env-vars-file=infra/run.env.local.yaml \
  --set-secrets="LLMIA_DEVICE_TOKEN=llmia-device-token:$LLMIA_DEVICE_SECRET_VERSION" \
  --min-instances=0 --max-instances=2 --timeout=45s --allow-unauthenticated
```

La URL permite abrir la pantalla de inicio. Las rutas de operador exigen Firebase y un UID permitido; las rutas de dispositivo exigen su credencial separada. No actives este acceso si una política de tu organización impide la invocación pública de Cloud Run. Ante una regresión, vuelve a la revisión anterior que pasó la prueba de humo desde Cloud Run.

## 5. Verificar el despliegue real

1. Comprueba `GET /health` y la pantalla inicial desde la URL HTTPS. Sin token, `GET /api/v1/robots/llmia-01/status` debe responder 401. Con una cuenta Firebase válida fuera de la lista, debe responder 403.
2. Desde `backend/`, instala las dependencias de desarrollo y ejecuta el simulador con `LLMIA_API_URL` apuntando a la URL de Cloud Run y `LLMIA_DEVICE_TOKEN` configurado en tu sesión: `python -m llmia.simulator --listen`. Detén el proceso al terminar para evitar tráfico y escrituras innecesarios.
3. Inicia sesión como operador autorizado. Pide dos capturas, comprueba que las dos fotos son distintas y que cada descripción corresponde a su foto. El resultado debe indicar simulación.
4. Verifica en consola que existen metadatos en Firestore `llmia-demo`, colección `llmia_robots`, y JPEG en el bucket privado. Confirma que una petición sin token no puede ver la foto. Verifica que la consulta de una observación previa funciona después de cambiar a una nueva revisión con la misma configuración de almacenamiento.
5. Registra URL, revisión, commit, fecha y resultados con el simulador antes de activar [Gemini](../docs/vision.md) e integrar el firmware.

**Estado de este corte:** pruebas de adaptadores con dobles locales y frontend/API verificados localmente. La imagen de contenedor, las credenciales reales y la persistencia en los servicios gestionados requieren comprobación en el proyecto compartido.

- [Permisos por base de datos de Firestore](https://docs.cloud.google.com/firestore/native/docs/manage-databases)
- [Recursos compartidos por las apps de Firebase](https://firebase.google.com/docs/projects/learn-more)
