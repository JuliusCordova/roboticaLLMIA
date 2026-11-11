"""Offline checks for the camera bridge; no real camera or Cloud credentials needed."""
from io import BytesIO
from unittest import TestCase
from unittest.mock import Mock

from PIL import Image

from llmia.camera_bridge import api_url_valid, camera_url_valid, grab


class BridgeTests(TestCase):
    def test_restrict_camera_to_local_endpoint(self):
        self.assertTrue(camera_url_valid("http://192.168.4.1/capture"))
        for bad in ("http://example.com/capture", "http://192.168.4.1/move?dir=adelante",
                    "http://192.168.4.1:8080/capture", "https://192.168.4.1/capture"):
            self.assertFalse(camera_url_valid(bad))

    def test_cloud_requires_https(self):
        self.assertTrue(api_url_valid("https://llmia-demo.example.run.app"))
        self.assertFalse(api_url_valid("http://llmia-demo.example.run.app"))

    def test_jpeg_is_validated(self):
        picture = BytesIO()
        Image.new("RGB", (32, 32), "blue").save(picture, format="JPEG")
        jpeg = picture.getvalue()
        response = Mock()
        response.raise_for_status.return_value = None
        response.iter_bytes.return_value = iter([jpeg])
        context = Mock()
        context.__enter__ = Mock(return_value=response)
        context.__exit__ = Mock(return_value=False)
        client = Mock()
        client.stream.return_value = context
        self.assertEqual(grab(client, "http://192.168.4.1/capture"), jpeg)
        response.iter_bytes.return_value = iter([b"not JPEG"])
        with self.assertRaises(ValueError):
            grab(client, "http://192.168.4.1/capture")
