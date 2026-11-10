"""Build LLMia using the registered Firebase web app's public configuration."""
import json
import re
import subprocess
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

PROJECT = "proyectopersonal-480420"
PROJECT_NUMBER = "913797829604"
APP_NAME = "llmia-web"
PUBLIC_HOST = f"llmia-demo-{PROJECT_NUMBER}.us-central1.run.app"
ROOT = Path(__file__).resolve().parents[1]


def read_json(url, token=None):
    headers = {"Authorization": f"Bearer {token}"} if token else {"Referer": f"https://{PUBLIC_HOST}/"}
    try:
        with urlopen(Request(url, headers=headers), timeout=15) as response:
            return json.load(response)
    except HTTPError as error:
        # Avoid printing a URL that includes the API key or the bearer token.
        raise RuntimeError(f"Firebase respondió HTTP {error.code}; verifica acceso y configuración.") from None
    except (URLError, TimeoutError, ValueError):
        raise RuntimeError("No se pudo consultar Firebase; revisa la conexión.") from None


def validate_config(config):
    if config.get("projectId") != PROJECT:
        raise RuntimeError("La app pertenece a otro proyecto.")
    if config.get("authDomain") != f"{PROJECT}.firebaseapp.com":
        raise RuntimeError("Dominio de autenticación inesperado.")
    if not re.fullmatch(r"AIza[A-Za-z0-9_-]{35}", config.get("apiKey", "")):
        raise RuntimeError("Firebase no devolvió un apiKey con formato válido.")
    if not re.fullmatch(rf"1:{PROJECT_NUMBER}:web:[A-Za-z0-9]+", config.get("appId", "")):
        raise RuntimeError("El appId no coincide con el proyecto aprobado.")


def load_config(token):
    apps = []
    next_page = None
    while True:
        query = {"pageSize": 100}
        if next_page:
            query["pageToken"] = next_page
        data = read_json(
            f"https://firebase.googleapis.com/v1beta1/projects/{PROJECT}/webApps?{urlencode(query)}",
            token,
        )
        apps.extend(app for app in data.get("apps", [])
                    if app.get("displayName") == APP_NAME and app.get("state", "ACTIVE") == "ACTIVE")
        next_page = data.get("nextPageToken")
        if not next_page:
            break
    if len(apps) != 1:
        raise RuntimeError("Debe existir exactamente una app web activa llamada llmia-web.")
    name = apps[0].get("name", "")
    if not name.startswith(f"projects/{PROJECT}/webApps/") and not name.startswith(f"projects/{PROJECT_NUMBER}/webApps/"):
        raise RuntimeError("Referencia de app Firebase inesperada.")
    config = read_json(f"https://firebase.googleapis.com/v1beta1/{quote(name, safe='/:')}/config", token)
    validate_config(config)
    public = read_json(f"https://identitytoolkit.googleapis.com/v1/projects?{urlencode({'key': config['apiKey']})}")
    if str(public.get("projectNumber")) != PROJECT_NUMBER:
        raise RuntimeError("El apiKey no corresponde al proyecto aprobado.")
    if PUBLIC_HOST not in public.get("authorizedDomains", []):
        raise RuntimeError("El dominio de Cloud Run aún no está autorizado en Firebase.")
    return config


def main():
    if subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=no"], cwd=ROOT, text=True).strip():
        raise RuntimeError("Hay cambios locales en archivos versionados; revísalos antes de compilar.")
    token = subprocess.check_output(
        ["gcloud", "auth", "print-access-token", "--project", PROJECT, "--quiet"], text=True,
    ).strip()
    config = load_config(token)
    revision = subprocess.check_output(["git", "rev-parse", "--short=12", "HEAD"], cwd=ROOT, text=True).strip()
    image = f"us-central1-docker.pkg.dev/{PROJECT}/llmia/web:{revision}-firebase"
    substitutions = ",".join([
        f"_IMAGE={image}", f"_FIREBASE_API_KEY={config['apiKey']}",
        f"_FIREBASE_AUTH_DOMAIN={config['authDomain']}", f"_FIREBASE_APP_ID={config['appId']}",
    ])
    print("PASS: configuración de llmia-web validada contra Firebase.", flush=True)
    print(f"Imagen de destino: {image}", flush=True)
    subprocess.run([
        "gcloud", "builds", "submit", ".", "--project", PROJECT, "--region", "us-central1",
        "--config=cloudbuild.yaml", f"--substitutions={substitutions}",
    ], cwd=ROOT, check=True)


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        raise SystemExit(str(error)) from None
