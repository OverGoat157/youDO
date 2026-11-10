import { useEffect, useRef, useState, type FormEvent } from 'react';
import { FormError } from '../auth/FormError';
import { jsonRequest, type Account, type ApiError, type ApiFetch, type Page, type Role } from '../auth/contracts';
import { generateTemporaryPassword } from './temporaryPassword';

const roleLabels: Record<Role, string> = { VIEWER: 'Просмотр', EDITOR: 'Редактор', ADMIN: 'Администратор' };
type Editor = { kind: 'create' } | { kind: 'edit' | 'reset'; account: Account };
export interface UsersPageProps { apiFetch: ApiFetch; currentUser: Account }
export function UsersPage({ apiFetch, currentUser }: UsersPageProps) {
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<Page<Account> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [editor, setEditor] = useState<Editor | null>(null);
  const createButton = useRef<HTMLButtonElement>(null);
  const returnFocus = useRef<HTMLElement | null>(null);
  const admin = currentUser.roles.includes('ADMIN') && !currentUser.mustChangePassword;
  useEffect(() => {
    if (!admin) return;
    let active = true;
    const controller = new AbortController();
    setLoading(true); setError(null);
    apiFetch<Page<Account>>(`/api/admin/users?page=${page}&size=20`, { signal: controller.signal })
      .then(data => { if (active) setResult(data); })
      .catch(err => { if (active) setError(err); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [apiFetch, admin, page, revision]);
  if (!admin) return <main className="page"><h1>Пользователи</h1><p role="alert">Управление аккаунтами доступно администратору после смены временного пароля.</p></main>;
  function open(value: Editor) { returnFocus.current = document.activeElement as HTMLElement; setEditor(value); }
  function close(saved = false) {
    setEditor(null);
    if (saved) setRevision(value => value + 1);
    requestAnimationFrame(() => (returnFocus.current?.isConnected ? returnFocus.current : createButton.current)?.focus());
  }
  return <main className="page" aria-labelledby="users-title">
    <header className="page-header"><div><h1 id="users-title">Учётные записи</h1><p className="muted">Аккаунты входа и права доступа</p></div>
      <button ref={createButton} onClick={() => open({ kind: 'create' })} disabled={!!editor}>Создать аккаунт</button></header>
    {editor && <AccountEditor key={editor.kind + ('account' in editor ? editor.account.id : '')} editor={editor} apiFetch={apiFetch} onClose={close} />}
    <FormError error={error} />
    {error && <button className="button--secondary" onClick={() => setRevision(v => v + 1)} disabled={loading}>Повторить загрузку</button>}
    {loading && <p role="status">Загружаем аккаунты…</p>}
    {!loading && !error && result?.content.length === 0 && <p className="empty-state">Аккаунтов на этой странице нет.</p>}
    {result && <>
      <div className="table-scroll" tabIndex={0} role="region" aria-label="Таблица аккаунтов" aria-busy={loading}>
        <table><caption className="visually-hidden">Учётные записи пользователей</caption><thead><tr><th scope="col">Имя / email</th><th scope="col">Роли</th><th scope="col">Статус</th><th scope="col">Действия</th></tr></thead>
          <tbody>{result.content.map(account => <tr key={account.id}>
            <td><strong>{account.name}</strong><br />{account.login}</td>
            <td>{account.roles.map(role => roleLabels[role]).join(', ')}</td>
            <td><span className={`badge ${account.active ? 'badge--active' : 'badge--inactive'}`}>{account.active ? 'Активен' : 'Отключён'}</span>
              {account.mustChangePassword && <span className="badge badge--warning">Нужна смена пароля</span>}</td>
            <td><div className="actions"><button className="button--secondary" disabled={!!editor || loading} onClick={() => open({ kind: 'edit', account })} aria-label={`Изменить ${account.login}`}>Изменить</button>
              <button className="button--secondary" disabled={!!editor || loading} onClick={() => open({ kind: 'reset', account })} aria-label={`Сбросить пароль ${account.login}`}>Сбросить пароль</button></div></td>
          </tr>)}</tbody></table>
      </div>
      <nav className="pagination" aria-label="Страницы аккаунтов">
        <button className="button--secondary" disabled={page === 0 || loading || !!editor} onClick={() => setPage(p => p - 1)}>Назад</button>
        <span aria-live="polite">Страница {page + 1} из {Math.max(1, result.totalPages)} · Всего {result.totalElements}</span>
        <button className="button--secondary" disabled={page + 1 >= result.totalPages || loading || !!editor} onClick={() => setPage(p => p + 1)}>Далее</button>
      </nav>
    </>}
  </main>;
}

function AccountEditor({ editor, apiFetch, onClose }: { editor: Editor; apiFetch: ApiFetch; onClose: (saved?: boolean) => void }) {
  const account = 'account' in editor ? editor.account : undefined;
  const [name, setName] = useState(account?.name ?? '');
  const [login, setLogin] = useState(account?.login ?? '');
  const [roles, setRoles] = useState<Role[]>(account?.roles ?? ['VIEWER']);
  const [active, setActive] = useState(account?.active ?? true);
  const [temporaryPassword, setTemporaryPassword] = useState(() => editor.kind === 'edit' ? '' : generateTemporaryPassword());
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const pending = useRef(false);
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus(); }, []);
  function close(saved = false) { setTemporaryPassword(''); onClose(saved); }
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (pending.current) return;
    if (editor.kind !== 'reset' && roles.length === 0) { setError(new Error('Выберите хотя бы одну роль')); return; }
    if (editor.kind !== 'edit' && !confirmed) { setError(new Error('Подтвердите передачу временного пароля')); return; }
    pending.current = true; setBusy(true); setError(null);
    try {
      if (editor.kind === 'reset') await apiFetch(`/api/admin/users/${editor.account.id}/reset-password`, jsonRequest('POST', { temporaryPassword }));
      else if (editor.kind === 'edit') await apiFetch(`/api/admin/users/${editor.account.id}`, jsonRequest('PUT', { name, login: login.trim().toLowerCase(), roles, active }));
      else await apiFetch('/api/admin/users', jsonRequest('POST', { name, login: login.trim().toLowerCase(), roles, temporaryPassword }));
      close(true);
    } catch (err) { setError(err as ApiError); }
    finally { pending.current = false; setBusy(false); }
  }
  return <section className="card account-editor" aria-labelledby="editor-title">
    <h2 id="editor-title" ref={heading} tabIndex={-1}>{editor.kind === 'create' ? 'Новый аккаунт' : editor.kind === 'edit' ? 'Изменить аккаунт' : 'Сбросить пароль'}</h2>
    {editor.kind === 'reset' && <p>Сброс для <strong>{account?.login}</strong> завершит все его сессии.</p>}
    <FormError error={error} />
    <form onSubmit={submit} aria-busy={busy}>
      {editor.kind !== 'reset' && <>
        <label htmlFor="account-name">Имя</label><input id="account-name" required maxLength={100} value={name} onChange={e => setName(e.target.value)} disabled={busy} />
        <label htmlFor="account-login">Email для входа</label><input id="account-login" type="email" autoComplete="off" required maxLength={150} value={login} onChange={e => setLogin(e.target.value)} disabled={busy} />
        <fieldset disabled={busy}><legend>Права доступа (минимум одна роль)</legend>{(Object.keys(roleLabels) as Role[]).map(role => <label className="check-label" key={role}>
          <input type="checkbox" checked={roles.includes(role)} onChange={e => setRoles(values => e.target.checked ? [...values, role] : values.filter(r => r !== role))} />{roleLabels[role]}</label>)}</fieldset>
        {editor.kind === 'edit' && <><label className="check-label"><input type="checkbox" checked={active} onChange={e => setActive(e.target.checked)} disabled={busy} />Аккаунт активен</label>
          <p className="muted">Отключение и изменение прав или email завершат сессии пользователя.</p></>}
      </>}
      {editor.kind !== 'edit' && <div className="temporary-password">
        <label htmlFor="temporary-password">Временный пароль</label><input id="temporary-password" type="text" autoComplete="off" spellCheck={false} readOnly value={temporaryPassword} aria-describedby="temporary-help" />
        <button type="button" className="button--secondary" disabled={busy} onClick={() => { setTemporaryPassword(generateTemporaryPassword()); setConfirmed(false); }}>Сгенерировать другой</button>
        <p id="temporary-help">Передайте пароль пользователю отдельно. При первом входе потребуется смена. После закрытия формы пароль больше не показывается.</p>
        <label className="check-label"><input type="checkbox" required checked={confirmed} onChange={e => setConfirmed(e.target.checked)} disabled={busy} />Я сохранил пароль для передачи и подтверждаю {editor.kind === 'reset' ? 'сброс' : 'создание'}</label>
      </div>}
      <div className="actions"><button type="submit" disabled={busy}>{busy ? 'Сохраняем…' : editor.kind === 'reset' ? 'Подтвердить сброс' : 'Сохранить'}</button>
        <button type="button" className="button--secondary" disabled={busy} onClick={() => close()}>Отмена</button></div>
    </form>
  </section>;
}
