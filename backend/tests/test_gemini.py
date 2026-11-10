from base64 import b64decode
from unittest import TestCase
from unittest.mock import Mock, patch

from llmia.gemini import VertexGeminiDescriber, vertex_describer
from llmia.http import create_app
from llmia.simulator import scene


def result(*parts, reason="STOP"):
    return {"candidates": [{"finishReason": reason, "content": {"parts": list(parts)}}]}


class GeminiTests(TestCase):
    def test_sends_exact_jpeg_and_extracts_only_visible_answer(self):
        generate = Mock(return_value=result(
            {"text": "razonamiento privado", "thought": True},
            {"text": "  Veo una caja.\n"},
        ))
        jpeg = scene("red")
        description = VertexGeminiDescriber(generate).describe(jpeg)
        self.assertEqual(description, "Veo una caja.")
        body = generate.call_args.args[0]
        image = body["contents"][0]["parts"][1]["inlineData"]
        self.assertEqual(image["mimeType"], "image/jpeg")
        self.assertEqual(b64decode(image["data"]), jpeg)
        self.assertNotIn("tools", body)
        self.assertEqual(body["generationConfig"]["candidateCount"], 1)

    def test_rejects_blocked_empty_partial_and_internal_only_responses(self):
        for response in (
            {"promptFeedback": {"blockReason": "SAFETY"}},
            result({"text": ""}),
            result({"text": "parcial"}, reason="MAX_TOKENS"),
            result({"text": "privado", "thought": True}),
        ):
            with self.subTest(response=response), self.assertRaises(ValueError):
                VertexGeminiDescriber(lambda _: response).describe(scene("blue"))

    def test_live_vision_cannot_be_enabled_in_local_mode(self):
        with patch.dict("os.environ", {"LLMIA_VISION_MODE": "gemini"}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "configuración GCP"):
                create_app()

    def test_live_vision_rejects_unapproved_model(self):
        with patch.dict("os.environ", {
            "LLMIA_VISION_MODE": "gemini",
            "LLMIA_STORAGE_MODE": "gcp",
            "LLMIA_GEMINI_MODEL": "another-model",
        }, clear=True):
            with self.assertRaisesRegex(RuntimeError, "Modelo o ubicación"):
                create_app()

    def test_vertex_transport_uses_project_and_managed_credentials(self):
        try:
            import google.auth.transport.requests
        except ImportError:
            self.skipTest("Install cloud requirements to test the Vertex transport.")
        response = Mock(status_code=200)
        response.json.return_value = result({"text": "Veo una caja."})
        with patch("google.auth.default", return_value=("adc", None)) as adc:
            with patch("google.auth.transport.requests.AuthorizedSession") as authorized:
                session = authorized.return_value.__enter__.return_value
                session.post.return_value = response
                adapter = vertex_describer("proyectopersonal-480420", "gemini-3.5-flash", "global")
                self.assertEqual(adapter.describe(scene("red")), "Veo una caja.")
        adc.assert_called_once()
        authorized.assert_called_once_with("adc", max_refresh_attempts=0, refresh_timeout=4)
        call = session.post.call_args
        self.assertEqual(call.args[0], "https://aiplatform.googleapis.com/v1/projects/proyectopersonal-480420"
                                       "/locations/global/publishers/google/models/gemini-3.5-flash:generateContent")
        self.assertFalse(call.kwargs["allow_redirects"])
        self.assertEqual(call.kwargs["timeout"], 12)
