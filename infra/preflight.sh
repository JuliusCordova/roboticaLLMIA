#!/usr/bin/env bash
# Read-only inventory. Never writes resources, credentials or gcloud config.
set -euo pipefail

project="${1:-proyectopersonal-480420}"
region="${2:-us-central1}"
if [[ "$project" != "proyectopersonal-480420" ]]; then
  printf '%s\n' 'LLMia está aprobado solo en proyectopersonal-480420.' >&2
  exit 2
fi
command -v gcloud >/dev/null || { printf '%s\n' 'Ejecuta este inventario en Google Cloud Shell.' >&2; exit 127; }

printf '%s\n' 'Proyecto compartido:'
gcloud projects describe "$project" --format='table(projectId,name,lifecycleState)'
printf '\n%s\n' 'Firestore de LLMia (vacío si aún no existe):'
gcloud firestore databases list --project="$project" --filter='name~"/databases/llmia-demo$"' --format='table(name,locationId,type)'
printf '\n%s\n' 'Bucket de LLMia (vacío si aún no existe):'
gcloud storage buckets list --project="$project" --filter="name=$project-llmia-images" --format='value(name)'
printf '\n%s\n' 'Cloud Run de LLMia (vacío si aún no existe):'
gcloud run services list --project="$project" --region="$region" --filter='metadata.name=llmia-demo' --format='value(metadata.name)'
printf '\n%s\n' 'Cuenta de ejecución de LLMia (vacío si aún no existe):'
gcloud iam service-accounts list --project="$project" --filter="email=llmia-runtime@$project.iam.gserviceaccount.com" --format='value(email)'
