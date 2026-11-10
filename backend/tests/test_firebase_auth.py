from datetime import UTC, datetime
from unittest import TestCase
from unittest.mock import patch

from fastapi.testclient import TestClient

from llmia.core import DescribeScene
from llmia.firebase_auth import AuthUnavailable, InvalidCredential, firebase_verifier
from llmia.http import create_app, http_app
from llmia.local import MemoryImages, MemoryObservations, SimulatedDescriber

BASE = "/api/v1/robots/llmia-01/status"


class FirebaseHttpTests(TestCase):
    def test_backend_uses_verified_identity_and_never_falls_back_to_local_token(self):
        def verifier(token):
            if token == "student":
                return "firebase-student", False
            if token == "authorized":
                return "firebase-operator", True
            if token == "network-failure":
                raise AuthUnavailable
            raise InvalidCredential

        core = DescribeScene(MemoryObservations(), MemoryImages(), SimulatedDescriber(), lambda: datetime.now(UTC))
        with TestClient(http_app(core, "llmia-01", verifier, "device-only")) as client:
            for token, expected in (
                ("authorized", 200), ("student", 403), ("device-only", 401),
                ("local-token", 401), ("network-failure", 503),
            ):
                with self.subTest(token=token):
                    response = client.get(BASE, headers={"Authorization": f"Bearer {token}"})
                    self.assertEqual(response.status_code, expected)
            self.assertEqual(client.post("/device/v1/poll", headers={"Authorization": "Bearer authorized"}).status_code, 401)

    def test_missing_allowlist_and_emulator_configurations_fail_closed(self):
        common = {
            "LLMIA_AUTH_MODE": "firebase",
            "LLMIA_DEVICE_TOKEN": "device-placeholder-for-test-only",
            "LLMIA_FIREBASE_PROJECT_ID": "demo-llmia",
        }
        for config in (common, {**common, "LLMIA_ALLOWED_UIDS": "operator", "FIREBASE_AUTH_EMULATOR_HOST": "localhost:9099"}):
            with self.subTest(emulator="FIREBASE_AUTH_EMULATOR_HOST" in config):
                with patch.dict("os.environ", config, clear=True), self.assertRaises(RuntimeError):
                    create_app()

    def test_firebase_adapter_checks_revocation_and_allowlist(self):
        try:
            from firebase_admin import auth
        except ImportError:
            self.skipTest("Install requirements-firebase.txt to test SDK integration.")
        with patch("firebase_admin.initialize_app", return_value="app") as init:
            verify = firebase_verifier("demo-llmia", frozenset({"operator"}))
            init.assert_called_once_with(options={"projectId": "demo-llmia"}, name="llmia-demo-llmia")
        with patch.object(auth, "verify_id_token", side_effect=[{"uid": "operator"}, {"uid": "student"}, auth.InvalidIdTokenError("invalid")]) as verify_token:
            self.assertEqual(verify("first"), ("operator", True))
            self.assertEqual(verify("second"), ("student", False))
            verify_token.assert_called_with("second", app="app", check_revoked=True)
            with self.assertRaises(InvalidCredential):
                verify("bad")
