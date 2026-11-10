FROM node:24-bookworm-slim AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
ARG VITE_FIREBASE_API_KEY
ARG VITE_FIREBASE_AUTH_DOMAIN
ARG VITE_FIREBASE_PROJECT_ID
ARG VITE_FIREBASE_APP_ID
RUN test -n "${VITE_FIREBASE_API_KEY}" && test -n "${VITE_FIREBASE_AUTH_DOMAIN}" \
    && test -n "${VITE_FIREBASE_PROJECT_ID}" && test -n "${VITE_FIREBASE_APP_ID}" \
    && npm run build

FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 LLMIA_STATIC_DIR=/app/web
WORKDIR /app
COPY backend/requirements*.txt ./
RUN pip install --no-cache-dir -r requirements.txt -r requirements-cloud.txt
RUN useradd --create-home --uid 10001 llmia
COPY backend/llmia ./llmia
COPY --from=web /web/dist ./web
USER 10001
CMD ["sh", "-c", "exec python -m uvicorn llmia.http:create_app --factory --host 0.0.0.0 --port \"${PORT:-8080}\""]
