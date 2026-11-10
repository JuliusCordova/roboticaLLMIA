from datetime import UTC, datetime, timedelta
from threading import Event, Thread
from unittest import TestCase

from fastapi.testclient import TestClient

from llmia.core import DescribeScene
from llmia.http import MAX_JPEG, local_app
from llmia.local import MemoryImages, MemoryObservations, SimulatedDescriber
from llmia.simulator import scene

ROBOT = "llmia-01"
BASE = "/api/v1/robots/llmia-01"
OPERATOR = {"Authorization": "Bearer operator"}
OTHER = {"Authorization": "Bearer other"}
DENIED = {"Authorization": "Bearer denied"}
DEVICE = {"Authorization": "Bearer device"}


class FlowTests(TestCase):
    def setUp(self):
        self.now = datetime(2026, 11, 10, tzinfo=UTC)
        self.core = DescribeScene(
            MemoryObservations(), MemoryImages(), SimulatedDescriber(), lambda: self.now,
        )
        app = local_app(self.core, ROBOT, {
            "operator": ("julio", frozenset({ROBOT})),
            "other": ("student", frozenset({ROBOT})),
            "denied": ("visitor", frozenset()),
        }, "device")
        self.client = TestClient(app)
        self.addCleanup(self.client.close)

    def create(self):
        response = self.client.post(BASE + "/observations", headers=OPERATOR)
        self.assertEqual(response.status_code, 202)
        self.assertEqual(response.json()["status"], "PENDIENTE")
        return response.json()["id"]

    def claim(self):
        return self.client.post("/device/v1/poll", headers=DEVICE)

    def upload(self, job, jpeg):
        return self.client.put(
            f"/device/v1/observations/{job}/capture",
            content=jpeg, headers={**DEVICE, "Content-Type": "image/jpeg"},
        )

    def get(self, job, headers=OPERATOR):
        return self.client.get(f"/api/v1/observations/{job}", headers=headers)

    def test_two_scenes_show_matching_image_and_description(self):
        ids = set()
        for color, label in (("red", "rojo"), ("blue", "azul")):
            job = self.create()
            self.assertNotIn(job, ids)
            ids.add(job)
            self.assertEqual(self.claim().json()["id"], job)
            self.assertEqual(self.get(job).json()["status"], "RECLAMADA")
            self.assertEqual(self.claim().status_code, 204)
            jpeg = scene(color)
            self.assertEqual(self.upload(job, jpeg).status_code, 200)
            result = self.get(job).json()
            self.assertEqual(result["status"], "LISTA")
            self.assertIn(label, result["description"])
            image = self.client.get(result["image_url"], headers=OPERATOR)
            self.assertEqual(image.headers["content-type"], "image/jpeg")
            self.assertEqual(image.content, jpeg)

    def test_separates_operator_and_robot_credentials_and_observation_owners(self):
        self.assertEqual(self.client.get(BASE + "/status").status_code, 401)
        self.assertEqual(self.client.get(BASE + "/status", headers=DENIED).status_code, 403)
        self.assertEqual(self.client.get(BASE + "/status", headers=DEVICE).status_code, 401)
        self.assertEqual(self.client.post("/device/v1/poll", headers=OPERATOR).status_code, 401)
        job = self.create()
        self.claim()
        ready = self.upload(job, scene("red")).json()
        self.assertEqual(self.get(job, OTHER).status_code, 404)
        self.assertEqual(self.client.get(ready["image_url"], headers=OTHER).status_code, 404)

    def test_heartbeat_status_ages_out(self):
        self.assertFalse(self.client.get(BASE + "/status", headers=OPERATOR).json()["online"])
        self.claim()
        self.assertTrue(self.client.get(BASE + "/status", headers=OPERATOR).json()["online"])
        self.now += timedelta(seconds=11)
        self.assertFalse(self.client.get(BASE + "/status", headers=OPERATOR).json()["online"])

    def test_expired_upload_cannot_replace_new_request(self):
        old = self.create()
        self.assertEqual(self.claim().json()["id"], old)
        self.assertEqual(self.client.post(BASE + "/observations", headers=OPERATOR).status_code, 409)
        self.now += timedelta(seconds=30)
        self.assertEqual(self.get(old).json()["status"], "VENCIDA")
        new = self.create()
        self.assertEqual(self.upload(old, scene("red")).status_code, 410)
        self.assertEqual(self.claim().json()["id"], new)

    def test_duplicate_upload_while_processing_cannot_run_inference_twice(self):
        started, release = Event(), Event()
        calls = []

        class Describer:
            def describe(self, data):
                calls.append(data)
                started.set()
                if not release.wait(5):
                    raise TimeoutError
                return "Escena de prueba"

        self.core.describer = Describer()
        job, jpeg = self.create(), scene("red")
        self.claim()
        result = []
        first = Thread(target=lambda: result.append(self.upload(job, jpeg)))
        first.start()
        try:
            self.assertTrue(started.wait(5))
            self.assertEqual(self.upload(job, jpeg).json()["status"], "ANALIZANDO")
            self.assertEqual(self.upload(job, scene("blue")).status_code, 409)
        finally:
            release.set()
            first.join(5)
        self.assertEqual(result[0].json()["status"], "LISTA")
        self.assertEqual(self.upload(job, jpeg).json()["status"], "LISTA")
        self.assertEqual(calls, [jpeg])

    def test_rejects_invalid_or_unclaimed_uploads(self):
        job = self.create()
        self.assertEqual(self.upload(job, scene("red")).status_code, 409)
        self.claim()
        self.assertEqual(self.upload(job, b"not an image").status_code, 415)
        self.assertEqual(self.upload(job, b"x" * (MAX_JPEG + 1)).status_code, 413)
        self.assertEqual(self.get(job).json()["status"], "RECLAMADA")

    def test_description_failure_does_not_publish_image(self):
        class BrokenDescriber:
            def describe(self, jpeg):
                raise RuntimeError("provider unavailable")

        self.core.describer = BrokenDescriber()
        job = self.create()
        self.claim()
        result = self.upload(job, scene("red")).json()
        self.assertEqual(result["status"], "ERROR")
        self.assertEqual(result["error"], "description_unavailable")
        self.assertIsNone(result["image_url"])
        self.assertIsNone(self.core.images.get(job))

    def test_result_that_finishes_after_expiration_is_not_published(self):
        owner = self

        class SlowDescriber:
            def describe(self, jpeg):
                owner.now += timedelta(seconds=31)
                return "Llegó tarde"

        self.core.describer = SlowDescriber()
        job = self.create()
        self.claim()
        result = self.upload(job, scene("blue")).json()
        self.assertEqual(result["status"], "VENCIDA")
        self.assertIsNone(result["description"])
        self.assertIsNone(self.core.images.get(job))
