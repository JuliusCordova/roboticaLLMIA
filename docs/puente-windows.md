# Puente Windows: ESP32-CAM original → Gemini (sin reprogramar)

## Arquitectura
El firmware original sigue anunciando `Carrito_CAM` y sirviendo `http://192.168.4.1/capture`. La laptop se conecta **por Wi-Fi al robot** y **por Ethernet a Internet**, y ejecuta `llmia.camera_bridge`. El puente usa el mismo contrato del simulador: `POST /device/v1/poll` y `PUT /device/v1/observations/{id}/capture`. El backend Cloud Run ya tiene un adaptador Gemini. **Los motores no se controlan desde este puente.**

## Requisitos
- Windows y Python 3.11+.
- Wi-Fi conectado a `Carrito_CAM` y Ethernet con Internet.
- Comprobar en la misma laptop: `http://192.168.4.1/capture` y `https://llmia-demo-913797829604.us-central1.run.app/health`.
- Descargar el repositorio o hacer `git pull`; abrir PowerShell en la raíz del repo.

## 1. Preparación local
```powershell
cd backend
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m llmia.camera_bridge --check-camera
```

Resultado esperado: `Cámara real OK: JPEG de ... bytes.`. Esta verificación **no necesita token de Cloud Run** ni guarda fotografías.

## 2. Puente autenticado (después de la prueba de cámara)
En PowerShell, define `LLMIA_DEVICE_TOKEN` como variable **temporal de sesión**, usando el valor activo de Secret Manager. No lo escribas en GitHub, el código, mensajes ni capturas. Una manera sin mostrarlo en consola, en una estación con `gcloud` autenticado y permisos para leer **solo** este secreto, es:

```powershell
$env:LLMIA_DEVICE_TOKEN = (gcloud secrets versions access 1 --secret=llmia-device-token --project=proyectopersonal-480420).Trim()
.\.venv\Scripts\python.exe -m llmia.camera_bridge
```

Si Windows no tiene `gcloud`, utiliza un método seguro aprobado para transferir el token al entorno local; no publiques su valor en el chat. Al terminar ejecuta `Remove-Item Env:\LLMIA_DEVICE_TOKEN`.

Inicia sesión en el panel LLMia con el operador autorizado y pulsa **¿Qué ves?** una vez. El puente reclamará el trabajo, descargará un JPEG actual de la ESP32-CAM y lo enviará a Cloud Run por HTTPS. Revisa que el panel presente exactamente esa foto y la descripción generada por Gemini.

## Límites de esta fase
- El servicio se ejecuta mientras la laptop y ambas conexiones sigan activas; no es todavía autonomía embarcada.
- Imagen JPEG máximo 512 KiB. Si excede el límite, se rechaza sin enviarla a la nube.
- El Wi-Fi local no está cifrado por HTTP, pero viaja sobre la red Wi-Fi protegida `Carrito_CAM`. Nunca expongas `/capture` al Internet público.
- El token del dispositivo representa una credencial privilegiada; su instalación en la laptop requiere cuidado, y debe revocarse si se divulga.
- Si cambia el contrato de Cloud Run, actualizar y volver a probar. El puente no habilita navegación ni control remoto de motores.
