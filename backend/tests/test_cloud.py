from datetime import UTC, datetime
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
from unittest.mock import Mock, patch

from fastapi.testclient import TestClient

from llmia.core import DescribeScene, Observation, RobotState, Status
from llmia.http import create_app, local_app
from llmia.local import MemoryImages, MemoryObservations, SimulatedDescriber


class CloudConfigurationTests(TestCase):
    def test_cloud_run_refuses_local_defaults(self):
        with patch.dict("os.environ", {"K_SERVICE": "llmia-demo"}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "Cloud Run requiere Firebase"):
                create_app()

    def test_cloud_refuses_wrong_project_database_bucket_and_emulators(self):
        base = {
            "LLMIA_AUTH_MODE": "firebase",
            "LLMIA_STORAGE_MODE": "gcp",
            "LLMIA_FIREBASE_PROJECT_ID": "llmia-dedicated",
            "LLMIA_CLOUD_PROJECT": "llmia-dedicated",
            "LLMIA_IMAGE_BUCKET": "llmia-dedicated-llmia-images",
            "LLMIA_FIRESTORE_DATABASE": "llmia-demo",
            "LLMIA_ALLOWED_UIDS": "operator",
            "LLMIA_DEVICE_TOKEN": "temporary-test-device-token",
        }
        for changes in (
            {"LLMIA_FIRESTORE_DATABASE": "(default)"},
            {"LLMIA_FIRESTORE_DATABASE": "smartroute"},
            {"LLMIA_IMAGE_BUCKET": "different-project-llmia-images"},
            {"LLMIA_FIREBASE_PROJECT_ID": "different-project"},
            {"FIRESTORE_EMULATOR_HOST": "localhost:8081"},
            {"STORAGE_EMULATOR_HOST": "localhost:4443"},
            {"LLMIA_CLOUD_PROJECT": "proyectopersonal-480420",
             "LLMIA_FIREBASE_PROJECT_ID": "proyectopersonal-480420",
             "LLMIA_IMAGE_BUCKET": "proyectopersonal-480420-llmia-images"},
        ):
            with self.subTest(changes=changes), patch.dict("os.environ", {**base, **changes}, clear=True):
                with self.assertRaisesRegex(RuntimeError, "proyecto dedicado"):
                    create_app()

    def test_static_frontend_and_protected_api_share_origin(self):
        with TemporaryDirectory() as directory:
            site = Path(directory)
            (site / "assets").mkdir()
            (site / "assets" / "app.js").write_text("window.llmia = true;")
            (site / "index.html").write_text('<main>LLMia</main>')
            core = DescribeScene(MemoryObservations(), MemoryImages(), SimulatedDescriber(), lambda: datetime.now(UTC))
            with TestClient(local_app(core, "robot", {}, "device", site)) as client:
                self.assertIn("LLMia", client.get("/").text)
                self.assertEqual(client.get("/assets/app.js").status_code, 200)
                self.assertEqual(client.get("/api/v1/robots/robot/status").status_code, 401)
                self.assertEqual(client.get("/api/unknown").status_code, 404)


class CloudAdapterTests(TestCase):
    def setUp(self):
        try:
            from llmia.cloud import FirestoreObservations, StorageImages
        except ImportError:
            self.skipTest("Install requirements-cloud.txt to exercise the Google SDK adapters.")
        self.firestore = FirestoreObservations
        self.storage = StorageImages

    def test_firestore_roundtrip_preserves_order_and_metadata(self):
        now = datetime.now(UTC)
        older = Observation("z-first", "robot", "user", now, Status.READY, "hash", "desc")
        newer = Observation("a-second", "robot", "user", now, Status.CLAIMED)
        state = RobotState(now, {older.id: older, newer.id: newer})
        wire = self.firestore.encode(state)
        decoded = self.firestore.decode(wire)
        self.assertEqual(list(decoded.observations), ["z-first", "a-second"])
        self.assertEqual(decoded, state)
        self.assertEqual(wire["observations"][0]["status"], "LISTA")
        self.assertNotIn("jpeg", wire["observations"][0])

    def test_firestore_writes_changed_state_inside_transaction_only(self):
        client, now = Mock(), datetime.now(UTC)
        repo = self.firestore(client)
        doc = client.collection.return_value.document.return_value
        doc.get.return_value.to_dict.return_value = None
        tx = client.transaction.return_value
        with patch("llmia.cloud.firestore.transactional", side_effect=lambda fn: fn):
            self.assertEqual(repo.transact("robot", lambda state: state.last_seen), None)
            tx.set.assert_not_called()
            repo.transact("robot", lambda state: setattr(state, "last_seen", now))
        doc.get.assert_called_with(transaction=tx, timeout=5, retry=None)
        tx.set.assert_called_once_with(doc, {"last_seen": now, "observations": []})

    def test_storage_prevents_overwrite_and_returns_same_bytes(self):
        bucket = Mock()
        jpeg = b"same-jpeg"
        store = self.storage(bucket)
        blob = bucket.blob.return_value
        blob.download_as_bytes.return_value = jpeg
        store.put("abc", jpeg)
        self.assertEqual(store.get("abc"), jpeg)
        bucket.blob.assert_called_with("observations/abc.jpg")
        blob.upload_from_string.assert_called_once_with(
            jpeg, content_type="image/jpeg", if_generation_match=0, timeout=8, retry=None,
        )
