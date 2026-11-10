import { useState, useRef, type FormEvent } from 'react';
import { FormError } from './FormError';
import { jsonRequest, type ApiError, type ApiFetch, type AuthSession } from './contracts';

export interface ChangePasswordPageProps { apiFetch: ApiFetch; session: Pick<AuthSession, 'clear'>; navigate: (path: string) => void; required?: boolean }
export function ChangePasswordPage({ apiFetch, session, navigate, required }: ChangePasswordPageProps) {
  const [currentPassword, setCurrent] = useState('');
  const [newPassword, setNew] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const pending = useRef(false);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (pending.current) return;
    if (newPassword !== confirmation) { setError(new Error('Новый пароль и подтверждение не совпадают')); return; }
    pending.current = true; setBusy(true); setError(null);
    try {
      await apiFetch<void>('/api/auth/change-password', jsonRequest('POST', { currentPassword, newPassword }));
      setCurrent(''); setNew(''); setConfirmation(''); setDone(true); session.clear();
    } catch (err) { setError(err as ApiError); }
    finally { pending.current = false; setBusy(false); }
  }
  return <main className="auth-layout"><section className="card auth-card" aria-labelledby="change-title">
    <h1 id="change-title">Смена пароля</h1>
    {done ? <div role="status"><p>Пароль изменён. Войдите с новым паролем.</p><button onClick={() => navigate('/login')}>Перейти ко входу</button></div> : <>
      {required && <p className="alert">Для продолжения замените временный пароль.</p>}
      <p id="password-policy">12–72 байта UTF-8, верхний и нижний регистр, цифра и спецсимвол. Кириллица и emoji занимают несколько байт.</p>
      <FormError error={error} />
      <form onSubmit={submit} aria-busy={busy}>
        <label htmlFor="current-password">Текущий пароль</label><input id="current-password" type="password" autoComplete="current-password" required value={currentPassword} onChange={e => setCurrent(e.target.value)} disabled={busy} />
        <label htmlFor="new-password">Новый пароль</label><input id="new-password" type="password" autoComplete="new-password" aria-describedby="password-policy" required value={newPassword} onChange={e => setNew(e.target.value)} disabled={busy} />
        <label htmlFor="confirm-password">Подтверждение пароля</label><input id="confirm-password" type="password" autoComplete="new-password" required value={confirmation} onChange={e => setConfirmation(e.target.value)} disabled={busy} />
        <button type="submit" disabled={busy}>{busy ? 'Сохраняем…' : 'Сменить пароль'}</button>
      </form>
    </>}
  </section></main>;
}
