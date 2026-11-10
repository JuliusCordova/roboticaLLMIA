"""Read-only permission check using the dedicated LLMia runtime identity."""
import google.auth
from google.api_core.exceptions import Forbidden
from google.auth import impersonated_credentials
from google.cloud import firestore

PROJECT = "proyectopersonal-480420"
RUNTIME = f"llmia-runtime@{PROJECT}.iam.gserviceaccount.com"
ALLOWED = "llmia-demo"
DENIED = ("(default)", "smartroute", "gestioncitas")
SCOPES = ["https://www.googleapis.com/auth/cloud-platform"]


def main():
    source, _ = google.auth.default(scopes=SCOPES)
    credentials = impersonated_credentials.Credentials(
        source_credentials=source, target_principal=RUNTIME, target_scopes=SCOPES, lifetime=300,
    )
    for database in (ALLOWED, *DENIED):
        client = firestore.Client(project=PROJECT, database=database, credentials=credentials)
        # Probe an unused path; do not open documents or collections of another app.
        try:
            client.document("_llmia_isolation_probe", "never-written").get(timeout=5, retry=None)
        except Forbidden:
            if database == ALLOWED:
                raise SystemExit(f"FAIL {database}: el runtime no tiene acceso.")
            print(f"PASS {database}: acceso rechazado.")
        else:
            if database != ALLOWED:
                raise SystemExit(f"FAIL {database}: el runtime tiene acceso y no debe desplegarse.")
            print(f"PASS {database}: lectura permitida.")


if __name__ == "__main__":
    main()
