"""Run a complete local observation with the same HTTP contract as the robot."""
from io import BytesIO
from os import environ
from sys import argv
from time import sleep
from urllib.parse import urlsplit

import httpx
from PIL import Image


def scene(color: str) -> bytes:
    result = BytesIO()
    Image.new("RGB", (96, 64), color).save(result, format="JPEG")
    return result.getvalue()


def main():
    operator = {"Authorization": f"Bearer {environ['LLMIA_OPERATOR_TOKEN']}"}
    device = {"Authorization": f"Bearer {environ['LLMIA_DEVICE_TOKEN']}"}
    with httpx.Client(base_url="http://127.0.0.1:8000", timeout=5, trust_env=False) as client:
        for color in ("red", "blue"):
            created = client.post("/api/v1/robots/llmia-01/observations", headers=operator)
            created.raise_for_status()
            job_id = created.json()["id"]
            claimed = client.post("/device/v1/poll", headers=device)
            claimed.raise_for_status()
            if claimed.json()["id"] != job_id:
                raise RuntimeError("La captura reclamada no coincide con la solicitud.")
            jpeg = scene(color)
            uploaded = client.put(
                f"/device/v1/observations/{job_id}/capture",
                content=jpeg,
                headers={**device, "Content-Type": "image/jpeg"},
            )
            uploaded.raise_for_status()
            response = client.get(f"/api/v1/observations/{job_id}", headers=operator)
            response.raise_for_status()
            result = response.json()
            if result["status"] != "LISTA":
                raise RuntimeError(f"La solicitud terminó con {result['status']}")
            image = client.get(result["image_url"], headers=operator)
            image.raise_for_status()
            if image.content != jpeg:
                raise RuntimeError("El JPEG publicado no coincide con el enviado.")
            print(f"{job_id} {result['status']} {result['description']}")


def listen():
    base_url = environ.get("LLMIA_API_URL", "http://127.0.0.1:8000")
    parsed = urlsplit(base_url)
    if (not parsed.hostname or parsed.username is not None or parsed.password is not None
            or parsed.query or parsed.fragment or parsed.path not in ("", "/")
            or not (parsed.scheme == "https" or (parsed.scheme == "http" and parsed.hostname in ("localhost", "127.0.0.1")))):
        raise ValueError("LLMIA_API_URL debe ser un origen HTTPS o localhost.")

    device = {"Authorization": f"Bearer {environ['LLMIA_DEVICE_TOKEN']}"}
    color = "red"
    with httpx.Client(base_url=base_url, timeout=15, trust_env=False) as client:
        print("Robot simulado conectado. Ctrl+C para detener.", flush=True)
        while True:
            response = client.post("/device/v1/poll", headers=device)
            response.raise_for_status()
            if response.status_code != 204:
                job = response.json()
                upload = client.put(
                    f"/device/v1/observations/{job['id']}/capture",
                    content=scene(color),
                    headers={**device, "Content-Type": "image/jpeg"},
                )
                if upload.status_code != 410:
                    upload.raise_for_status()
                    print(f"{job['id']} {upload.json()['status']} ({color})", flush=True)
                    color = "blue" if color == "red" else "red"
            sleep(2)


if __name__ == "__main__":
    if argv[1:] == ["--listen"]:
        try:
            listen()
        except KeyboardInterrupt:
            pass
    else:
        main()
