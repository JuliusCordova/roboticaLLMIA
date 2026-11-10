import { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider, onAuthStateChanged, signInWithPopup, signOut } from 'firebase/auth';
import { callApi, explain } from './api';
import { authErrorMessage } from './auth-error';
import './style.css';

const root = '/api/v1/robots/llmia-01';
const localMode = import.meta.env.DEV && import.meta.env.VITE_LOCAL_DEMO === 'true';
const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};
const auth = !localMode && Object.values(config).every(Boolean) ? getAuth(initializeApp(config)) : null;
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const statusText = {
  PENDIENTE: 'Esperando al robot…',
  RECLAMADA: 'Tomando la fotografía…',
  ANALIZANDO: 'Preparando la descripción…',
};

function App() {
  const [user, setUser] = useState(null);
  const [authReady, setAuthReady] = useState(!auth);
  const [localToken, setLocalToken] = useState('');
  const [authError, setAuthError] = useState('');
  const [signingIn, setSigningIn] = useState(false);

  useEffect(() => {
    if (auth) return onAuthStateChanged(auth, (account) => {
      setUser(account ? { name: account.displayName || account.email, getToken: () => account.getIdToken() } : null);
      setAuthReady(true);
    });
  }, []);

  async function login() {
    setAuthError('');
    setSigningIn(true);
    try {
      await signInWithPopup(auth, new GoogleAuthProvider());
    } catch (error) {
      setAuthError(authErrorMessage(error));
    } finally {
      setSigningIn(false);
    }
  }

  async function logout() {
    setAuthError('');
    try {
      if (auth) await signOut(auth);
      else setUser(null);
    } catch {
      setAuthError('No se pudo cerrar la sesión. Vuelve a intentar.');
    }
  }

  return (
    <div className="layout">
      <header className="top">
        <a className="brand" href="/" aria-label="Inicio de LLMia"><span className="mark" aria-hidden="true">L</span> LLMia</a>
        <span className="course">Taller de Robótica · UPN</span>
        {user && <button className="secondary exit" onClick={logout}>Cerrar sesión</button>}
      </header>
      <main>
        {authError && <p className="error" role="alert">{authError}</p>}
        {!authReady ? <p role="status">Comprobando sesión…</p> : user ? <Panel user={user} /> : (
          <section className="login card" aria-labelledby="login-title">
            <span className="eyebrow">Robot explorador</span>
            <h1 id="login-title">Mira a través de LLMia</h1>
            <p>Pide una foto nueva al robot y consulta qué hay frente a su cámara.</p>
            {localMode ? (
              <form onSubmit={(event) => {
                event.preventDefault();
                const token = localToken.trim();
                if (!token) return;
                setUser({ name: 'Operador local', getToken: async () => token });
                setLocalToken('');
              }}>
                <label htmlFor="local-token">Token local del operador</label>
                <input id="local-token" type="password" autoComplete="off" required value={localToken} onChange={(event) => setLocalToken(event.target.value)} />
                <button className="primary" type="submit">Entrar al simulador</button>
              </form>
            ) : auth ? (
              <button className="primary" disabled={signingIn} onClick={login}>{signingIn ? 'Conectando…' : 'Continuar con Google'}</button>
            ) : <p className="error">El inicio de sesión aún no está configurado para este entorno.</p>}
          </section>
        )}
      </main>
      <footer>LLMia · Fast Demo</footer>
    </div>
  );
}

function Panel({ user }) {
  const [robot, setRobot] = useState(null);
  const [connectionError, setConnectionError] = useState('');
  const [pending, setPending] = useState('');
  const [result, setResult] = useState(null);
  const [imageUrl, setImageUrl] = useState(null);
  const [error, setError] = useState('');
  const active = useRef(null);

  useEffect(() => {
    let timer;
    const controller = new AbortController();
    async function refresh() {
      try {
        const state = await callApi(`${root}/status`, user.getToken, { signal: controller.signal });
        if (controller.signal.aborted) return;
        setRobot(state);
        setConnectionError('');
      } catch (failure) {
        if (controller.signal.aborted) return;
        setRobot(null);
        setConnectionError(explain(failure));
      }
      if (!controller.signal.aborted) timer = setTimeout(refresh, 3000);
    }
    refresh();
    return () => {
      controller.abort();
      clearTimeout(timer);
      active.current?.abort();
    };
  }, [user]);

  useEffect(() => {
    if (!result) {
      setImageUrl(null);
      return;
    }
    const url = URL.createObjectURL(result.blob);
    setImageUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [result]);

  async function observe() {
    if (active.current || !robot?.online) return;
    const controller = new AbortController();
    active.current = controller;
    const { signal } = controller;
    setError('');
    setResult(null);
    setImageUrl(null);
    setPending('Solicitando captura…');
    try {
      let job = await callApi(`${root}/observations`, user.getToken, { method: 'POST', signal });
      const url = `/api/v1/observations/${encodeURIComponent(job.id)}`;
      const end = Date.now() + 33_000;
      while (statusText[job.status] && !signal.aborted && Date.now() < end) {
        setPending(statusText[job.status]);
        await wait(750);
        if (signal.aborted) return;
        job = await callApi(url, user.getToken, { signal });
      }
      if (signal.aborted) return;
      if (job.status !== 'LISTA') throw new Error(job.error || 'capture_timeout');
      const blob = await callApi(`${url}/image`, user.getToken, { image: true, signal });
      if (!signal.aborted) setResult({ description: job.description, id: job.id, blob });
    } catch (failure) {
      if (!signal.aborted) setError(explain(failure));
    } finally {
      if (active.current === controller) {
        active.current = null;
        if (!signal.aborted) setPending('');
      }
    }
  }

  const status = connectionError ? 'Estado no disponible' : robot?.online ? 'Robot conectado' : robot ? 'Robot sin conexión' : 'Comprobando conexión';
  return (
    <>
      <section className="intro">
        <span className="eyebrow">Panel de exploración</span>
        <h1>Observa el entorno</h1>
        <p>Solicita una imagen nueva desde la cámara frontal de LLMia.</p>
      </section>
      <div className="workspace">
        <section className="card scene" aria-labelledby="scene-title">
          <h2 id="scene-title">Cámara del robot</h2>
          <div className="camera">
            {imageUrl ? <img src={imageUrl} alt="Fotografía de la observación actual" /> : (
              <div className="placeholder"><span aria-hidden="true">◎</span><p>{pending || 'Aquí aparecerá la próxima foto.'}</p></div>
            )}
          </div>
          {result && <p className="caption">Captura {result.id.slice(0, 8)}</p>}
        </section>
        <section className="card controls" aria-labelledby="control-title">
          <span className={`status ${robot?.online ? 'online' : ''}`} role="status">{status}</span>
          <h2 id="control-title">Pedir una observación</h2>
          <p>El robot toma una foto para responder la pregunta.</p>
          <button className="primary" disabled={Boolean(pending) || !robot?.online} onClick={observe}>¿Qué ves?</button>
          {pending && <p className="feedback" role="status">{pending}</p>}
          {connectionError && <p className="error" role="alert">{connectionError}</p>}
          {error && <p className="error" role="alert">{error}</p>}
          {result && <div className="description" role="status"><h3>Descripción</h3><p>{result.description}</p></div>}
          <p className="caption">Sesión: {user.name}</p>
        </section>
      </div>
    </>
  );
}

createRoot(document.getElementById('root')).render(<App />);
