export function authErrorMessage(error) {
  const code = typeof error?.code === 'string' && /^auth\/[a-z-]{1,70}$/.test(error.code)
    ? error.code : null;
  const messages = {
    'auth/invalid-api-key': 'La configuración de Firebase no es válida. Es necesario reconstruir el panel.',
    'auth/unauthorized-domain': 'El dominio del panel no está autorizado en Firebase.',
    'auth/popup-blocked': 'El navegador bloqueó la ventana de Google. Permite ventanas emergentes para este sitio.',
    'auth/popup-closed-by-user': 'Se cerró la ventana de Google antes de completar el acceso.',
    'auth/cancelled-popup-request': 'Ya hay un inicio de sesión en curso. Completa la ventana de Google.',
    'auth/operation-not-allowed': 'El proveedor de Google no está habilitado en Firebase.',
    'auth/network-request-failed': 'No se pudo conectar con Firebase. Revisa tu conexión.',
  };
  const message = messages[code] || 'No se pudo iniciar sesión con Google. Vuelve a intentar.';
  return code ? `${message} (${code})` : message;
}
