const messages = {
  invalid_operator_token: 'Tu sesión no es válida. Vuelve a iniciar sesión.',
  robot_forbidden: 'Tu cuenta no tiene permiso para usar LLMia.',
  capture_already_active: 'Ya hay una captura en curso. Intenta de nuevo en unos segundos.',
  authentication_unavailable: 'La verificación de acceso no está disponible.',
  capture_timeout: 'El robot no completó la captura a tiempo. Intenta de nuevo.',
  description_unavailable: 'No se pudo generar la descripción. Intenta otra captura.',
};

export function explain(error) {
  return messages[error?.message] || 'No se pudo completar la solicitud. Revisa la conexión e intenta de nuevo.';
}

export async function callApi(path, getToken, { method = 'GET', image = false, signal } = {}) {
  const access = await getToken();
  const response = await fetch(path, {
    method,
    headers: { Authorization: `Bearer ${access}` },
    cache: 'no-store',
    signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(8000)]) : AbortSignal.timeout(8000),
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    throw new Error(payload.detail || String(response.status));
  }
  return image ? response.blob() : response.json();
}
