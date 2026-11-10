#!/usr/bin/env bash
# Read-only inventory. Never writes resources, credentials or gcloud config.
set -euo pipefail

project="${1:?Uso: bash infra/preflight.sh PROJECT_ID [REGION]}"
region="${2:-us-central1}"
if [[ ! "$project" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ || "$project" == "proyectopersonal-480420" ]]; then
  printf '%s\n' 'Usa el ID del proyecto GCP dedicado a LLMia; SmartRoute queda excluido.' >&2
  exit 2
fi
command -v gcloud >/dev/null || { printf '%s\n' 'Ejecuta este inventario en Google Cloud Shell.' >&2; exit 127; }

gcloud projects describe "$project" --format='table(projectId,name,lifecycleState)'
gcloud services list --enabled --project="$project" --format='value(config.name)'
gcloud firestore databases list --project="$project" --format='table(name,locationId,type)'
gcloud storage buckets list --project="$project" --format='value(name)'
gcloud run services list --project="$project" --region="$region" --format='value(metadata.name)'
