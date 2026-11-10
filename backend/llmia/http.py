"""HTTP input adapters and composition root for the local demonstration."""
from datetime import UTC, datetime
from io import BytesIO
from os import environ
from secrets import compare_digest

from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.responses import JSONResponse
from PIL import Image
from starlette.concurrency import run_in_threadpool

from .core import DescribeScene, Observation, Rejected, Status
from .local import MemoryImages, MemoryObservations, SimulatedDescriber

MAX_JPEG = 512 * 1024
MAX_PIXELS = 2_100_000
ERRORS = {
    "capture_already_active": 409,
    "capture_not_claimed": 409,
    "capture_timeout": 410,
    "observation_not_found": 404,
    "image_not_found": 404,
    "image_not_ready": 409,
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


def local_app(
    use_case: DescribeScene,
    robot_id: str,
    operators: dict[str, tuple[str, frozenset[str]]],
    device_token: str,
):
    app = FastAPI(title="LLMia FAST DEMO · Simulación local")

    @app.exception_handler(Rejected)
    async def handle_error(_request, exc):
        return JSONResponse({"detail": exc.code}, status_code=ERRORS[exc.code])

    def token(request: Request) -> str:
        scheme, _, value = request.headers.get("Authorization", "").partition(" ")
        if scheme.lower() != "bearer" or not value.isascii() or not value:
            raise HTTPException(401, "token_required", headers={"WWW-Authenticate": "Bearer"})
        return value

    def operator(credential: str = Depends(token)):
        for expected, (owner, allowed_robots) in operators.items():
            if compare_digest(credential, expected):
                if robot_id not in allowed_robots:
                    raise HTTPException(403, "robot_forbidden")
                return owner
        raise HTTPException(401, "invalid_operator_token")

    def device(credential: str = Depends(token)):
        if not compare_digest(credential, device_token):
            raise HTTPException(401, "invalid_device_token")

    def check_robot(requested: str):
        if requested != robot_id:
            raise HTTPException(404, "robot_not_found")

    @app.get("/health")
    def health():
        return {"status": "ok", "mode": "simulator"}

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

    return app


def create_app():
    operator_token = environ.get("LLMIA_OPERATOR_TOKEN", "")
    device_token = environ.get("LLMIA_DEVICE_TOKEN", "")
    if (any(len(t) < 24 or not t.isascii() or not t.isprintable() or " " in t
            for t in (operator_token, device_token)) or operator_token == device_token):
        raise RuntimeError("Configura dos tokens ASCII distintos, sin espacios y de al menos 24 caracteres.")
    robot = "llmia-01"
    use_case = DescribeScene(MemoryObservations(), MemoryImages(), SimulatedDescriber(), lambda: datetime.now(UTC))
    return local_app(use_case, robot, {operator_token: ("operador-local", frozenset({robot}))}, device_token)
