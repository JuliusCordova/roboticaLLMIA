"""Use the existing ESP32-CAM /capture endpoint as an LLMia device bridge.

Laptop network setup: Wi-Fi to Carrito_CAM and Ethernet to Internet.
No changes to the ESP32-CAM firmware. Never commit the device token.
"""
from __future__ import annotations

import argparse
from io import BytesIO
import os
import time
from urllib.parse import urlsplit

import httpx
from PIL import Image

DEFAULT_API = "https://llmia-demo-913797829604.us-central1.run.app"
DEFAULT_CAMERA = "http://192.168.4.1/capture"
MAX_JPEG = 512 * 1024
MAX_PIXELS = 2_100_000


def camera_url_valid(url: str) -> bool:
    p = urlsplit(url)
    return (p.scheme == "http" and p.hostname == "192.168.4.1"
            and p.port in (None, 80) and p.path == "/capture"
            and not (p.username or p.password or p.query or p.fragment))


def api_url_valid(url: str) -> bool:
    p = urlsplit(url)
    return (p.scheme == "https" and bool(p.hostname)
            and p.path in ("", "/") and not (p.username or p.password or p.query or p.fragment))


def grab(client: httpx.Client, url: str) -> bytes:
    with client.stream("GET", url, headers={"Accept": "image/jpeg", "Cache-Control": "no-cache"}) as r:
        r.raise_for_status()
        data = bytearray()
        for chunk in r.iter_bytes():
            if len(data) + len(chunk) > MAX_JPEG:
                raise ValueError("JPEG demasiado grande (máximo 512 KiB).")
            data.extend(chunk)
    jpeg = bytes(data)
    try:
        with Image.open(BytesIO(jpeg), formats=["JPEG"]) as img:
            if img.width * img.height > MAX_PIXELS:
                raise ValueError("Dimensiones JPEG excesivas.")
            img.load()
    except (OSError, Image.DecompressionBombError) as e:
        raise ValueError("La cámara no devolvió un JPEG válido.") from e
    return jpeg


def main() -> None:
    parser = argparse.ArgumentParser(description="Puente LLMia: cámara local hacia Cloud Run.")
    parser.add_argument("--check-camera", action="store_true", help="Probar /capture sin usar credenciales.")
    parser.add_argument("--api-url", default=DEFAULT_API)
    parser.add_argument("--camera-url", default=DEFAULT_CAMERA)
    args = parser.parse_args()
    if not camera_url_valid(args.camera_url):
        parser.error("La cámara debe ser http://192.168.4.1/capture")
    with httpx.Client(timeout=10, follow_redirects=False, trust_env=False) as camera:
        if args.check_camera:
            frame = grab(camera, args.camera_url)
            print(f"Cámara real OK: JPEG de {len(frame)} bytes.")
            return

        if not api_url_valid(args.api_url):
            parser.error("Cloud Run debe ser una URL HTTPS sin ruta ni parámetros.")
        token = os.environ.get("LLMIA_DEVICE_TOKEN", "")
        if len(token) < 24 or not token.isascii() or any(c.isspace() for c in token):
            parser.error("Configura LLMIA_DEVICE_TOKEN en el entorno local; no lo pegues en el código.")
        headers = {"Authorization": f"Bearer {token}"}
        with httpx.Client(base_url=args.api_url, timeout=20, follow_redirects=False, trust_env=False) as cloud:
            print("Puente conectado. Cámara local + Cloud Run. Ctrl+C para detener.", flush=True)
            try:
                while True:
                    try:
                        response = cloud.post("/device/v1/poll", headers=headers)
                        response.raise_for_status()
                        if response.status_code != 204:
                            job_id = response.json()["id"]
                            frame = grab(camera, args.camera_url)
                            upload = cloud.put(
                                f"/device/v1/observations/{job_id}/capture",
                                content=frame,
                                headers={**headers, "Content-Type": "image/jpeg"},
                            )
                            if upload.status_code == 410:
                                print(f"{job_id} solicitud vencida; esperar otra.", flush=True)
                            else:
                                upload.raise_for_status()
                                print(f"{job_id} imagen real enviada; estado: {upload.json().get('status')}", flush=True)
                    except (httpx.RequestError, httpx.HTTPStatusError, ValueError, KeyError) as exc:
                        print(f"Error en puente: {type(exc).__name__}: {str(exc).splitlines()[0][:180]}", flush=True)
                        if isinstance(exc, httpx.HTTPStatusError) and exc.response.status_code in (401, 403):
                            print("Autenticación rechazada. Se detiene el puente.", flush=True)
                            return
                    time.sleep(2)
            except KeyboardInterrupt:
                print("Puente detenido.")


if __name__ == "__main__":
    main()
