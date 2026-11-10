import { useState, useRef, type FormEvent } from 'react';
import { FormError } from './FormError';
import type { ApiError, AuthSession } from './contracts';

export interface LoginPageProps { session: AuthSession; navigate: (path: string) => void }
export function LoginPage({ session, navigate }: LoginPageProps) {
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const [error, setError] = useState<ApiError | null>(null);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (pending.current) return;
    pending.current = true; setBusy(true); setError(null);
    try {
      const { user } = await session.login({ login: login.trim().toLowerCase(), password, clientType: 'WEB' });
      setPassword(''); navigate(user.mustChangePassword ? '/change-password' : '/orders');
    } catch (err) { setError(err as ApiError); }
    finally { pending.current = false; setBusy(false); }
  }
  return <main className="auth-layout"><section className="card auth-card" aria-labelledby="login-title">
    <h1 id="login-title">Вход в youDO</h1><p className="muted">Используйте email учётной записи.</p>
    <FormError error={error} login />
    <form onSubmit={submit} aria-busy={busy}>
      <label htmlFor="login">Email</label><input id="login" type="email" autoComplete="username" required maxLength={150} value={login} onChange={e => setLogin(e.target.value)} disabled={busy} />
      <label htmlFor="password">Пароль</label><input id="password" type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} disabled={busy} />
      <button type="submit" disabled={busy}>{busy ? 'Входим…' : 'Войти'}</button>
    </form>
  </section></main>;
}
