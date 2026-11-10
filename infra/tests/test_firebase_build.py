import importlib.util
from pathlib import Path
from unittest import TestCase
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("firebase_build", Path(__file__).parents[1] / "build_from_firebase.py")
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


def config():
    return {"apiKey": "AIza" + "a" * 35, "projectId": build.PROJECT,
            "authDomain": f"{build.PROJECT}.firebaseapp.com", "appId": f"1:{build.PROJECT_NUMBER}:web:abcdef"}


class FirebaseBuildTests(TestCase):
    def test_rejects_bad_pasted_key_or_wrong_project(self):
        for change in ({"apiKey": "AIza" + "a" * 152}, {"apiKey": "invalid"},
                       {"projectId": "other"}, {"authDomain": "other.firebaseapp.com"}, {"appId": "1:other:web:test"}):
            with self.subTest(change=change), self.assertRaises(RuntimeError):
                build.validate_config({**config(), **change})

    def test_reads_registered_configuration_and_checks_authorized_host(self):
        responses = [
            {"apps": [{"displayName": "llmia-web", "state": "ACTIVE", "name": f"projects/{build.PROJECT}/webApps/test"}]},
            config(),
            {"projectNumber": build.PROJECT_NUMBER, "authorizedDomains": [build.PUBLIC_HOST]},
        ]
        with patch.object(build, "read_json", side_effect=responses):
            self.assertEqual(build.load_config("temporary-token"), config())

    def test_stops_before_build_if_public_firebase_validation_fails(self):
        with patch.object(build, "read_json", side_effect=[
            {"apps": [{"displayName": "llmia-web", "name": f"projects/{build.PROJECT}/webApps/test"}]},
            config(), {"projectNumber": build.PROJECT_NUMBER, "authorizedDomains": []},
        ]):
            with self.assertRaisesRegex(RuntimeError, "dominio"):
                build.load_config("temporary-token")
