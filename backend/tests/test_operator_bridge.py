from datetime import UTC, datetime
from unittest import TestCase
from unittest.mock import Mock

from fastapi.testclient import TestClient

from llmia.core import DescribeScene
from llmia.http import local_app
from llmia.local import MemoryImages, MemoryObservations
from llmia.simulator import scene

BASE = "/api/v1/robots/llmia-01"


def bearer(value):
    return {"Authorization": "Bearer " + value}


class OperatorBridgeTests(TestCase):
    def setUp(self):
        self.describer = Mock()
        self.describer.describe.return_value = "No veo un perro en esta imagen."
        self.core = DescribeScene(MemoryObservations(), MemoryImages(), self.describer,
                                  lambda: datetime(2026, 11, 19, tzinfo=UTC))
        self.client = TestClient(local_app(self.core, "llmia-01", {
            "julio-token": ("julio", frozenset({"llmia-01"})),
            "companion-token": ("companion", frozenset({"llmia-01"})),
            "denied-token": ("visitor", frozenset()),
        }, "device-secret"))
        self.addCleanup(self.client.close)

    def test_authorized_companion_uses_own_session_and_question_with_fresh_image(self):
        question = "Busca un perro y descríbelo"
        headers = bearer("companion-token")
        created = self.client.post(BASE + "/observations", headers=headers, json={"question": question})
        self.assertEqual(created.status_code, 202)
        job = created.json()["id"]
        self.assertEqual(created.json()["question"], question)
        self.assertEqual(self.client.post(BASE + "/bridge/poll", headers=headers).json()["id"], job)
        jpeg = scene("red")
        result = self.client.put(BASE + f"/bridge/observations/{job}/capture", content=jpeg,
                                 headers={**headers, "Content-Type": "image/jpeg"})
        self.assertEqual(result.status_code, 200)
        self.assertEqual(result.json()["status"], "LISTA")
        self.describer.describe.assert_called_once_with(jpeg, question)
        new = self.client.post(BASE + "/observations", headers=headers, json={"question": "¿Hay una silla?"})
        self.assertNotEqual(new.json()["id"], job)
        self.assertEqual(self.client.post(BASE + "/bridge/poll", headers=headers).json()["id"], new.json()["id"])
        self.assertEqual(new.json()["status"], "PENDIENTE")

    def test_bridge_rejects_missing_invalid_denied_and_device_credentials(self):
        for headers, expected in (({}, 401), (bearer("expired"), 401),
                                  (bearer("denied-token"), 403), (bearer("device-secret"), 401)):
            self.assertEqual(self.client.post(BASE + "/bridge/poll", headers=headers).status_code, expected)
            self.assertEqual(self.client.put(BASE + "/bridge/observations/unknown/capture",
                                             headers=headers).status_code, expected)
        self.describer.describe.assert_not_called()

    def test_other_operator_cannot_claim_or_upload_another_operators_job(self):
        job = self.client.post(BASE + "/observations", headers=bearer("julio-token")).json()["id"]
        other = bearer("companion-token")
        self.assertEqual(self.client.post(BASE + "/bridge/poll", headers=other).status_code, 204)
        self.assertEqual(self.client.post(BASE + "/bridge/poll", headers=bearer("julio-token")).status_code, 200)
        self.assertEqual(self.client.put(BASE + f"/bridge/observations/{job}/capture", content=scene("red"),
                                         headers={**other, "Content-Type": "image/jpeg"}).status_code, 404)
        self.describer.describe.assert_not_called()

    def test_robot_scope_is_checked(self):
        self.assertEqual(self.client.post("/api/v1/robots/other/bridge/poll", headers=bearer("julio-token")).status_code, 404)

    def test_invalid_questions_do_not_create_jobs(self):
        for question in ("", "  ", "x" * 501, 17, None, "bad\x00text"):
            self.assertEqual(self.client.post(BASE + "/observations", headers=bearer("julio-token"),
                                             json={"question": question}).status_code, 422)
        self.assertEqual(self.client.post(BASE + "/bridge/poll", headers=bearer("julio-token")).status_code, 204)

    def test_legacy_device_contract_and_missing_question_are_compatible(self):
        job = self.client.post(BASE + "/observations", headers=bearer("julio-token")).json()
        self.assertEqual(job["question"], "¿Qué ves?")
        self.assertEqual(self.client.post("/device/v1/poll", headers=bearer("device-secret")).json()["id"], job["id"])

    def test_upload_still_requires_claim_and_valid_jpeg(self):
        h = bearer("julio-token")
        job = self.client.post(BASE + "/observations", headers=h).json()["id"]
        path = BASE + f"/bridge/observations/{job}/capture"
        self.assertEqual(self.client.put(path, content=scene("blue"), headers={**h, "Content-Type": "image/jpeg"}).status_code, 409)
        self.client.post(BASE + "/bridge/poll", headers=h)
        self.assertEqual(self.client.put(path, content=b"invalid", headers={**h, "Content-Type": "image/jpeg"}).status_code, 415)
        self.describer.describe.assert_not_called()
