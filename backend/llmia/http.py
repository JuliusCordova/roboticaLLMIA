"""HTTP input adapters and composition root for the local demonstration."""
from datetime import UTC, datetime
from io import BytesIO
from os import environ
from pathlib import Path
from secrets import compare_digest
from typing import Callable

from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from PIL import Image
from starlette.concurrency import run_in_threadpool

from .core import DescribeScene, Observation, Rejected, RepositoryUnavailable, Status
from .firebase_auth import AuthUnavailable, InvalidCredential, firebase_verifier
from .gemini import LOCATION as GEMINI_LOCATION, MODEL as GEMINI_MODEL, VertexGeminiDescriber, vertex_describer
from .local import MemoryImages, MemoryObservations, SimulatedDescriber

CLOUD_PROJECT = "proyectopersonal-480420"
CLOUD_DATABASE = "llmia-demo"
CLOUD_IMAGE_BUCKET = f"{CLOUD_PROJECT}-llmia-images"

MAX_JPEG = 512 * 1024
MAX_PIXELS = 2_100_000
ERRORS = {
    "capture_already_active": 409,
    "capture_not_claimed": 409,
    "capture_timeout": 410,
    "observation_not_found": 404,
    "image_not_found": 404,
    "image_not_ready": 409,
    "image_unavailable": 503,
}


def payload(job: Observation):
    return {
        "id": job.id,
        "robot_id": job.robot,
        "status": job.status,
        "expires_at": job.expires_at.isoformat(),
        "description": job.description,
        "error": job.error,
        "image_url": f"/api/v1/observations/{job.id}/image" if job.status == Status.READY else None,
    }


def http_app(
    use_case: DescribeScene,
    robot_id: str,
    verify_operator: Callable[[str], tuple[str, bool]],
    device_token: str,
    static_dir: Path | None = None,
):
    app = FastAPI(title="LLMia FAST DEMO · Simulación local")

    @app.exception_handler(Rejected)
    async def handle_error(_request, exc):
        return JSONResponse({"detail": exc.code}, status_code=ERRORS[exc.code])

    @app.exception_handler(RepositoryUnavailable)
    async def unavailable(_request, _exc):
        return JSONResponse({"detail": "storage_unavailable"}, status_code=503)

    def token(request: Request) -> str:
        scheme, _, value = request.headers.get("Authorization", "").partition(" ")
        if scheme.lower() != "bearer" or not value.isascii() or not value:
            raise HTTPException(401, "token_required", headers={"WWW-Authenticate": "Bearer"})
        return value

    def operator(credential: str = Depends(token)):
        try:
            owner, allowed = verify_operator(credential)
        except InvalidCredential as exc:
            raise HTTPException(401, "invalid_operator_token", headers={"WWW-Authenticate": "Bearer"}) from exc
        except AuthUnavailable as exc:
            raise HTTPException(503, "authentication_unavailable") from exc
        if not allowed:
            raise HTTPException(403, "robot_forbidden")
        return owner

    def device(credential: str = Depends(token)):
        if not compare_digest(credential, device_token):
            raise HTTPException(401, "invalid_device_token")

    def check_robot(requested: str):
        if requested != robot_id:
            raise HTTPException(404, "robot_not_found")

    @app.get("/health")
    def health():
        return {"status": "ok", "mode": "gemini" if isinstance(use_case.describer, VertexGeminiDescriber) else "simulator"}

    @app.get("/api/v1/robots/{robot_id}/status")
    def robot_status(request: Request, _owner: str = Depends(operator)):
        check_robot(request.path_params["robot_id"])
        online, last_seen, active = use_case.status(robot_id)
        return {"robot_id": robot_id, "online": online, "last_seen": last_seen, "active_status": active}

    @app.post("/api/v1/robots/{robot_id}/observations", status_code=202)
    def create_observation(request: Request, owner: str = Depends(operator)):
        check_robot(request.path_params["robot_id"])
        return payload(use_case.request(robot_id, owner))

    @app.get("/api/v1/observations/{job_id}")
    def read_observation(job_id: str, owner: str = Depends(operator)):
        return payload(use_case.get(robot_id, owner, job_id))

    @app.get("/api/v1/observations/{job_id}/image")
    def read_image(job_id: str, owner: str = Depends(operator)):
        return Response(
            use_case.image(robot_id, owner, job_id),
            media_type="image/jpeg",
            headers={"Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"},
        )

    @app.post("/device/v1/poll", dependencies=[Depends(device)])
    def poll():
        job = use_case.poll(robot_id)
        if job is None:
            return Response(status_code=204)
        return {"id": job.id, "action": "CAPTURE", "expires_at": job.expires_at.isoformat()}

    @app.put("/device/v1/observations/{job_id}/capture", dependencies=[Depends(device)])
    async def receive_capture(job_id: str, request: Request):
        if request.headers.get("content-type", "").split(";")[0].strip().lower() != "image/jpeg":
            raise HTTPException(415, "jpeg_required")
        body = bytearray()
        async for chunk in request.stream():
            if len(body) + len(chunk) > MAX_JPEG:
                raise HTTPException(413, "jpeg_too_large")
            body.extend(chunk)

        def receive():
            jpeg = bytes(body)
            try:
                with Image.open(BytesIO(jpeg), formats=["JPEG"]) as image:
                    if image.width * image.height > MAX_PIXELS:
                        raise HTTPException(413, "jpeg_dimensions_too_large")
                    image.load()
            except (OSError, ValueError, Image.DecompressionBombError) as exc:
                raise HTTPException(415, "invalid_jpeg") from exc
            return payload(use_case.receive(robot_id, job_id, jpeg))

        return await run_in_threadpool(receive)

    if static_dir is not None:
        if not (static_dir / "index.html").is_file() or not (static_dir / "assets").is_dir():
            raise RuntimeError("No se encontró el frontend compilado.")
        app.mount("/assets", StaticFiles(directory=static_dir / "assets"), name="assets")

        @app.get("/", include_in_schema=False)
        def index():
            return FileResponse(static_dir / "index.html", headers={"Cache-Control": "no-cache"})

    return app


def local_app(
    use_case: DescribeScene,
    robot_id: str,
    operators: dict[str, tuple[str, frozenset[str]]],
    device_token: str,
    static_dir: Path | None = None,
):
    def verify(credential: str):
        for expected, (owner, allowed_robots) in operators.items():
            if compare_digest(credential, expected):
                return owner, robot_id in allowed_robots
        raise InvalidCredential

    return http_app(use_case, robot_id, verify, device_token, static_dir)


def valid_local_token(value: str) -> bool:
    return len(value) >= 24 and value.isascii() and value.isprintable() and " " not in value


def create_app():
    mode = environ.get("LLMIA_AUTH_MODE", "local")
    storage_mode = environ.get("LLMIA_STORAGE_MODE", "memory")
    vision_mode = environ.get("LLMIA_VISION_MODE", "simulator")
    static = environ.get("LLMIA_STATIC_DIR", "").strip()
    static_dir = Path(static) if static else None
    if mode not in ("local", "firebase") or storage_mode not in ("memory", "gcp"):
        raise RuntimeError("Modo de autenticación o almacenamiento desconocido.")
    if environ.get("K_SERVICE") and (mode != "firebase" or storage_mode != "gcp" or static_dir is None):
        raise RuntimeError("Cloud Run requiere Firebase, almacenamiento GCP y frontend compilado.")

    if vision_mode not in ("simulator", "gemini") or (vision_mode == "gemini" and storage_mode != "gcp"):
        raise RuntimeError("Gemini solo puede activarse con la configuración GCP de LLMia.")

    model = environ.get("LLMIA_GEMINI_MODEL", GEMINI_MODEL)
    location = environ.get("LLMIA_GEMINI_LOCATION", GEMINI_LOCATION)
    if vision_mode == "gemini" and (model, location) != (GEMINI_MODEL, GEMINI_LOCATION):
        raise RuntimeError("Modelo o ubicación de Gemini no admitidos por esta demo.")

    device_token = environ.get("LLMIA_DEVICE_TOKEN", "")
    if not valid_local_token(device_token):
        raise RuntimeError("Configura LLMIA_DEVICE_TOKEN con al menos 24 caracteres ASCII sin espacios.")

    if mode == "firebase":
        auth_project = environ.get("LLMIA_FIREBASE_PROJECT_ID", "").strip()
        uids = frozenset(uid.strip() for uid in environ.get("LLMIA_ALLOWED_UIDS", "").split(",") if uid.strip())
        if not auth_project or not uids or environ.get("FIREBASE_AUTH_EMULATOR_HOST"):
            raise RuntimeError("Firebase requiere un proyecto real y UIDs permitidos; el emulador está deshabilitado.")

    if storage_mode == "gcp":
        project = environ.get("LLMIA_CLOUD_PROJECT", "").strip()
        database = environ.get("LLMIA_FIRESTORE_DATABASE", "").strip()
        bucket = environ.get("LLMIA_IMAGE_BUCKET", "").strip()
        if (mode != "firebase" or project != CLOUD_PROJECT or project != auth_project
                or database != CLOUD_DATABASE or bucket != CLOUD_IMAGE_BUCKET
                or environ.get("FIRESTORE_EMULATOR_HOST") or environ.get("STORAGE_EMULATOR_HOST")):
            raise RuntimeError("Cloud Run debe usar el proyecto aprobado, la base llmia-demo y el bucket exclusivo de LLMia.")
        from .cloud import cloud_adapters

        repository, images = cloud_adapters(project, database, bucket)
    else:
        repository, images = MemoryObservations(), MemoryImages()

    robot = "llmia-01"
    describer = vertex_describer(project, model, location) if vision_mode == "gemini" else SimulatedDescriber()
    use_case = DescribeScene(repository, images, describer, lambda: datetime.now(UTC))
    if mode == "firebase":
        return http_app(use_case, robot, firebase_verifier(auth_project, uids), device_token, static_dir)
    operator_token = environ.get("LLMIA_OPERATOR_TOKEN", "")
    if not valid_local_token(operator_token) or operator_token == device_token:
        raise RuntimeError("Configura LLMIA_OPERATOR_TOKEN con un valor distinto al del dispositivo.")
    return local_app(use_case, robot, {operator_token: ("operador-local", frozenset({robot}))}, device_token, static_dir)
