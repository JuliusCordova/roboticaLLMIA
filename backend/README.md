# Backend local de LLMia

Primer corte de la [especificación FAST DEMO](../specs/SPEC.md). Valida por HTTP el flujo de solicitud, captura JPEG, descripción simulada y consulta de la misma imagen. El simulador distingue dos escenas de prueba por color; no ejecuta Gemini ni reconoce objetos.

- `llmia/core.py`: casos de uso, reglas y puertos sin dependencias de nube.
- `llmia/http.py`: API y credenciales locales distintas para operador y dispositivo.
- `llmia/local.py`: adaptadores de almacenamiento en memoria y descripción simulada.
- `llmia/simulator.py`: cliente que crea dos solicitudes y actúa como dispositivo usando el contrato HTTP.

## Ejecutar la prueba

Desde `backend/`, con Python 3.11 o superior y un entorno virtual:

```bash
python -m pip install -r requirements-dev.txt
python -m unittest discover -s tests -v
export LLMIA_OPERATOR_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"
export LLMIA_DEVICE_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"
python -m uvicorn llmia.http:create_app --factory --host 127.0.0.1 --port 8000
```

Desde otra terminal, con las mismas dos variables y el mismo entorno, ejecuta `python -m llmia.simulator`. Se esperan dos solicitudes distintas en estado `LISTA`, una para el JPEG rojo y otra para el azul. El script compara byte a byte el JPEG recuperado con el que envió. La API interactiva queda en `http://127.0.0.1:8000/docs`.

Este corte se ejecuta en un solo proceso y en loopback. Los datos se pierden al reiniciar y se retienen como máximo las últimas 16 solicitudes. Los tokens locales son solo para la simulación. Para usar [React y Firebase Auth](../frontend/README.md), ejecuta el simulador como dispositivo con `python -m llmia.simulator --listen`. Hay adaptadores Firestore y Cloud Storage y un [runbook para el despliegue](../infra/README.md). El [adaptador Gemini](../docs/vision.md) es opcional y queda desactivado en la prueba local. El flujo desplegado de Cloud Run y una inferencia con Gemini a partir de una imagen del simulador fueron observados el 10-11-2026. Faltan las pruebas formales completas de persistencia y autorización, la voz y la conexión HTTPS de la placa real. Ver [bitácora](../docs/progreso-integracion-2026-11-10.md). Este backend no acciona motores.
