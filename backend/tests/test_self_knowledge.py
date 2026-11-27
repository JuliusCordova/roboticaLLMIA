import json
import unittest
from llmia.gemini import VertexGeminiDescriber
from llmia.self_knowledge import ANSWERS


class SelfKnowledgeTests(unittest.TestCase):
    def classify(self, data, finish="STOP"):
        def generate(request):
            self.assertIn("IDENTITY", request["generationConfig"]["responseSchema"]["properties"]["intent"]["enum"])
            return {"candidates": [{"finishReason": finish, "content": {"parts": [{"text": json.dumps(data)}]}}]}
        return VertexGeminiDescriber(generate).self_intent("¿Quién eres?")

    def test_all_approved_categories(self):
        for intent, answer in ANSWERS.items():
            with self.subTest(intent=intent):
                self.assertEqual(self.classify({"intent": intent}), intent)
                self.assertTrue(0 < len(answer) <= 700)

    def test_unknown_and_team(self):
        for intent in ("UNKNOWN", "PRESENT_TEAM"):
            self.assertEqual(self.classify({"intent": intent}), intent)

    def test_reject_unapproved_and_malformed(self):
        for data in ({"intent": "MOVE"}, {"intent": "IDENTITY", "answer": "fake"}, {"intent": []}, [], None):
            self.assertEqual(self.classify(data), "UNKNOWN")
        self.assertEqual(self.classify({"intent": "IDENTITY"}, "MAX_TOKENS"), "UNKNOWN")
