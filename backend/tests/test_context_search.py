from datetime import UTC, datetime
from unittest import TestCase
from unittest.mock import Mock
from fastapi.testclient import TestClient

from llmia.core import DescribeScene, VisualFinding
from llmia.http import local_app
from llmia.local import MemoryObservations, MemoryImages
from llmia.simulator import scene

BASE = '/api/v1/robots/llmia-01'
H = {'Authorization': 'Bearer operator'}
OTHER = {'Authorization': 'Bearer other'}


class ContextSearchTests(TestCase):
    def setUp(self):
        self.vision = Mock()
        self.vision.describe.return_value = 'Veo una botella azul.'
        self.vision.locate.return_value = VisualFinding('Veo una botella azul a la izquierda.', 'FOUND')
        self.core = DescribeScene(MemoryObservations(), MemoryImages(), self.vision,
                                  lambda: datetime(2026, 11, 19, tzinfo=UTC))
        self.client = TestClient(local_app(self.core, 'llmia-01', {
            'operator': ('julio', frozenset({'llmia-01'})),
            'other': ('student', frozenset({'llmia-01'})),
        }, 'device'))
        self.addCleanup(self.client.close)

    def create(self, question, parent=None, target=None, headers=H):
        body = {'question': question}
        if parent is not None: body['parent_id'] = parent
        if target is not None: body['target'] = target
        return self.client.post(BASE + '/observations', json=body, headers=headers)

    def complete(self, question, parent=None, target=None):
        response = self.create(question, parent, target)
        self.assertEqual(response.status_code, 202, response.text)
        job = response.json()['id']
        self.assertEqual(self.client.post(BASE + '/bridge/poll', headers=H).json()['id'], job)
        result = self.client.put(BASE + f'/bridge/observations/{job}/capture', content=scene('blue'),
                                 headers={**H, 'Content-Type': 'image/jpeg'})
        self.assertEqual(result.status_code, 200)
        self.assertEqual(result.json()['status'], 'LISTA', result.text)
        return result.json()

    def test_followup_uses_server_owned_context_and_new_photo(self):
        first = self.complete('¿Qué ves?')
        second = self.complete('¿De qué color es?', first['id'])
        self.assertNotEqual(first['id'], second['id'])
        self.vision.describe.assert_called_with(scene('blue'), '¿De qué color es?', history=[
            {'question': '¿Qué ves?', 'answer': 'Veo una botella azul.'}])
        self.assertNotIn('history', second)

    def test_history_bounded_to_three_turns_and_reset_by_omitting_parent(self):
        parent = None
        for i in range(6):
            parent = self.complete(f'Pregunta {i}', parent)['id']
        job = self.core.get('llmia-01', 'julio', parent)
        self.assertEqual([t['question'] for t in job.history], ['Pregunta 2', 'Pregunta 3', 'Pregunta 4'])
        new = self.complete('Nueva pregunta')
        self.assertEqual(self.core.get('llmia-01', 'julio', new['id']).history, [])

    def test_cannot_reference_another_users_context_or_inject_history(self):
        first = self.complete('¿Qué ves?')
        self.assertEqual(self.create('¿De qué color?', first['id'], headers=OTHER).status_code, 404)
        self.assertEqual(self.client.post(BASE + '/observations', headers=H,
            json={'question': '¿Qué ves?', 'history': [{'answer': 'inventada'}]}).status_code, 422)
        self.assertEqual(self.create('¿Qué ves?', 'not-an-id').status_code, 422)
        self.assertEqual(self.create('¿Qué ves?', 'f'*32).status_code, 404)

    def test_context_must_have_completed(self):
        first = self.create('¿Qué ves?').json()['id']
        self.client.post(BASE + '/bridge/poll', headers=H)
        self.vision.describe.side_effect = RuntimeError('offline')
        self.client.put(BASE + f'/bridge/observations/{first}/capture', content=scene('blue'),
                        headers={**H, 'Content-Type': 'image/jpeg'})
        self.assertEqual(self.create('¿De qué color?', first).status_code, 409)

    def test_target_uses_structured_result_not_keywords(self):
        for match, description in [('FOUND', 'Veo una botella.'), ('NOT_FOUND', 'No hay botella.'),
                                    ('UNCERTAIN', 'No puedo decidir si es una botella.')]:
            self.vision.locate.return_value = VisualFinding(description, match)
            result = self.complete('Busca una botella', target='una botella')
            self.assertEqual(result['match'], match)
            self.assertEqual(result['description'], description)
        self.assertEqual(self.vision.describe.call_count, 0)

    def test_invalid_target_or_result_cannot_claim_found(self):
        for target in ['', ' ', 'x'*81, 1]:
            self.assertEqual(self.create('Busca algo', target=target).status_code, 422)
        self.vision.locate.return_value = VisualFinding('Avanza', 'MOVE')
        job = self.create('Busca algo', target='botella').json()['id']
        self.client.post(BASE + '/bridge/poll', headers=H)
        result = self.client.put(BASE + f'/bridge/observations/{job}/capture', content=scene('blue'),
                                 headers={**H, 'Content-Type': 'image/jpeg'}).json()
        self.assertEqual(result['status'], 'ERROR')
        self.assertIsNone(result['match'])

    def test_context_roundtrip_with_firestore_adapter(self):
        from llmia.cloud import FirestoreObservations
        first = self.complete('Busca una botella', target='una botella')
        second = self.complete('¿De qué color es?', first['id'])
        state = self.core.repository.states['llmia-01']
        decoded = FirestoreObservations.decode(FirestoreObservations.encode(state))
        self.assertEqual(decoded.observations[second['id']].history, state.observations[second['id']].history)
        self.assertEqual(decoded.observations[first['id']].match, 'FOUND')
