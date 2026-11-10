import assert from 'node:assert/strict';
import test from 'node:test';
import { authErrorMessage } from '../src/auth-error.js';

test('diagnoses Firebase configuration errors', () => {
  assert.match(authErrorMessage({ code: 'auth/invalid-api-key' }), /reconstruir.*auth\/invalid-api-key/);
  assert.match(authErrorMessage({ code: 'auth/unauthorized-domain' }), /dominio.*Firebase/);
});
test('distinguishes popup blockers from provider configuration', () => {
  assert.match(authErrorMessage({ code: 'auth/popup-blocked' }), /ventanas emergentes/);
  assert.match(authErrorMessage({ code: 'auth/operation-not-allowed' }), /proveedor de Google/);
});
test('does not render arbitrary errors, keys or provider messages', () => {
  assert.equal(authErrorMessage({ code: 'secret-key', message: 'private-detail' }),
               'No se pudo iniciar sesión con Google. Vuelve a intentar.');
  assert.equal(authErrorMessage(undefined), 'No se pudo iniciar sesión con Google. Vuelve a intentar.');
});
