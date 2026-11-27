"""Private, allowlisted presentation assets; no robot actuation."""
import json
from io import BytesIO
from PIL import Image

PREFIX = "multimedia/equipo/"
MAX_PHOTO = 4 * 1024 * 1024
MAX_METADATA = 16 * 1024

def read_bounded(bucket, name, limit):
    blob = bucket.blob(PREFIX + name)
    with blob.open("rb") as stream:
        data = stream.read(limit + 1)
    if not data or len(data) > limit:
        raise ValueError("invalid_asset_size")
    return data

def team_presentation(bucket):
    metadata = json.loads(read_bounded(bucket, "integrantes.json", MAX_METADATA))
    names = metadata["integrantes"]
    if not isinstance(names, list) or not 1 <= len(names) <= 20:
        raise ValueError("invalid_members")
    if any(not isinstance(n, str) or not 1 <= len(n.strip()) <= 100 or not n.isprintable() for n in names):
        raise ValueError("invalid_member_name")
    return {"members": [n.strip() for n in names],
            "image_url": "/api/v1/presentation/team/image"}

def team_photo(bucket):
    data = read_bounded(bucket, "grupo.jpg", MAX_PHOTO)
    with Image.open(BytesIO(data), formats=["JPEG"]) as image:
        if image.width * image.height > 12_000_000:
            raise ValueError("invalid_dimensions")
        image.verify()
    return data
