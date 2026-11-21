import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { linkProps, navigate } from '../../app/router';
import { ApiRequestError } from '../../api/client';
import { canEdit, isAdmin, type Account } from '../../api/session';
import {
  archiveOrder, createNote, deleteNote, deleteOrderPermanently, getOrder,
  listNotes, restoreOrder, updateNote,
} from './api';
import {
  formatBudget, formatDateTime, formatDeadline,
  type OrderNote, type OrderResponse,
} from './types';
import { ArchivedBadge, ConfirmDialog, PriorityBadge, StatusBadge } from './components';
import './orders.css';

export interface OrderCardPageProps {
  user: Account;
  orderId: string;
}

type Pending = null | 'archive' | 'restore' | 'delete';

export function OrderCardPage({ user, orderId }: OrderCardPageProps) {
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [revision, setRevision] = useState(0);
  const [notes, setNotes] = useState<OrderNote[]>([]);
  const [notesError, setNotesError] = useState<ApiRequestError | null>(null);
  const [notesLoading, setNotesLoading] = useState(true);
  const [notesRevision, setNotesRevision] = useState(0);
  const [pending, setPending] = useState<Pending>(null);
  const [actionBusy, setActionBusy] = useState(false);
  const [notice, setNotice] = useState<{ kind: 'success' | 'error' | 'info'; text: string } | null>(null);
  const editLink = useRef<HTMLAnchorElement>(null);
  const editable = canEdit(user);
  const admin = isAdmin(user);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    getOrder(orderId, controller.signal)
      .then(setOrder)
      .catch(cause => {
        if (cause instanceof DOMException && cause.name === 'AbortError') return;
        setError(cause as ApiRequestError);
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [orderId, revision]);

  useEffect(() => {
    const controller = new AbortController();
    setNotesLoading(true);
    setNotesError(null);
    listNotes(orderId, controller.signal)
      .then(setNotes)
      .catch(cause => {
        if (cause instanceof DOMException && cause.name === 'AbortError') return;
        setNotesError(cause as ApiRequestError);
      })
      .finally(() => setNotesLoading(false));
    return () => controller.abort();
  }, [orderId, notesRevision]);

  const refresh = useCallback(() => {
    setRevision(value => value + 1);
    setNotesRevision(value => value + 1);
  }, []);

  async function runAction(kind: Exclude<Pending, null>) {
    if (!order) return;
    setActionBusy(true);
    setNotice(null);
    try {
      if (kind === 'archive') {
        await archiveOrder(order.id, order.version);
        setNotice({ kind: 'success', text: 'Заказ перенесён в архив. Запись сохранена и доступна для восстановления.' });
        refresh();
      } else if (kind === 'restore') {
        await restoreOrder(order.id, order.version);
        setNotice({ kind: 'success', text: 'Заказ восстановлен из архива с прежним статусом.' });
        refresh();
      } else {
        await deleteOrderPermanently(order.id, order.version);
        navigate('/orders', { replace: true });
      }
    } catch (cause) {
      const api = cause as ApiRequestError;
      if (api.status === 409) {
        setNotice({
          kind: 'error',
          text: 'Заказ уже изменён другим пользователем. Введённые данные не перезаписываются: перечитайте карточку и повторите действие.',
        });
      } else if (api.status === 403) {
        setNotice({ kind: 'error', text: 'Удалять заказы навсегда может только администратор.' });
      } else if (api.status === 404) {
        setNotice({ kind: 'error', text: 'Заказ не найден или уже удалён.' });
      } else {
        setNotice({ kind: 'error', text: api.message || 'Действие не выполнено.' });
      }
    } finally {
      setActionBusy(false);
      setPending(null);
    }
  }

  if (loading && !order) return <main className="page"><p role="status" className="muted">Загружаем карточку заказа…</p></main>;

  if (error) {
    const notFound = error.status === 404;
    return (
      <main className="page">
        <h1>{notFound ? 'Заказ не найден' : 'Не удалось открыть заказ'}</h1>
        <div className="alert alert--error" role="alert">
          <p>{notFound ? 'Проверьте адрес карточки: запись могла быть удалена.' : error.message}</p>
          {error.correlationId && <small>Код обращения: {error.correlationId}</small>}
          {!notFound && <p><button type="button" className="button--secondary" onClick={refresh}>Повторить загрузку</button></p>}
        </div>
        <p><a {...linkProps('/orders')}>Вернуться к списку заказов</a></p>
      </main>
    );
  }

  if (!order) return null;

  return (
    <main className="page" aria-labelledby="order-title">
      <p className="breadcrumbs"><a {...linkProps('/orders')}>Заказы</a> / {order.code}</p>
      <header className="page-header">
        <div>
          <h1 id="order-title">{order.title}</h1>
          <p className="muted mono">{order.code}</p>
          <p className="badges">
            <StatusBadge status={order.status} />
            <PriorityBadge priority={order.priority} />
            <ArchivedBadge archived={order.archived} />
            {order.urgent && <span className="badge badge--overdue">Срочный</span>}
          </p>
        </div>
        <div className="actions">
          <button type="button" className="button--secondary" onClick={refresh} disabled={loading}>Обновить</button>
          {editable && !order.archived && (
            <a className="button" ref={editLink} {...linkProps(`/orders/${order.id}/edit`)}>Изменить</a>
          )}
          {editable && !order.archived && (
            <button type="button" className="button--secondary" onClick={() => setPending('archive')} disabled={actionBusy}>В архив</button>
          )}
          {editable && order.archived && (
            <button type="button" className="button--secondary" onClick={() => setPending('restore')} disabled={actionBusy}>Восстановить</button>
          )}
          {admin && order.archived && (
            <button type="button" className="button--danger" onClick={() => setPending('delete')} disabled={actionBusy}>Удалить навсегда</button>
          )}
        </div>
      </header>

      {notice && (
        <div className={`alert ${notice.kind === 'success' ? 'alert--success' : notice.kind === 'error' ? 'alert--error' : ''}`} role={notice.kind === 'error' ? 'alert' : 'status'}>
          <p>{notice.text}</p>
          {notice.kind === 'error' && <button type="button" className="button--secondary" onClick={refresh}>Перечитать карточку</button>}
        </div>
      )}

      <section className="card card--details" aria-labelledby="details-title">
        <h2 id="details-title">Условия заказа</h2>
        <dl className="details-grid">
          <div><dt>Бюджет</dt><dd>{formatBudget(order.budget)}</dd></div>
          <div><dt>Дедлайн</dt><dd>{formatDeadline(order.deadline)}</dd></div>
          <div><dt>Категория</dt><dd>{order.categoryName ?? '—'}</dd></div>
          <div><dt>Заказчик</dt><dd>{order.customerName}</dd></div>
          <div><dt>Исполнитель</dt><dd>{order.freelancerName ?? 'не назначен'}</dd></div>
          <div><dt>Создан</dt><dd>{formatDateTime(order.createdAt)}</dd></div>
          <div><dt>Изменён</dt><dd>{formatDateTime(order.updatedAt)}</dd></div>
        </dl>
        <h3>Описание</h3>
        <p className="preserve-lines">{order.description || 'Описание не заполнено.'}</p>
        <h3>Теги</h3>
        {order.tags.length > 0
          ? <p className="badges">{order.tags.map(tag => <span key={tag} className="badge">{tag}</span>)}</p>
          : <p className="muted">Тегов нет.</p>}
      </section>

      <NotesSection
        orderId={order.id}
        notes={notes}
        loading={notesLoading}
        error={notesError}
        editable={editable && !order.archived}
        onChanged={() => { setNotesRevision(value => value + 1); setRevision(value => value + 1); }}
        onRetry={() => setNotesRevision(value => value + 1)}
      />

      <ConfirmDialog
        open={pending === 'archive'}
        title="Перенести заказ в архив?"
        description={<p>Запись останется в базе, её можно восстановить.</p>}
        confirmLabel="Перенести в архив"
        busy={actionBusy}
        onConfirm={() => runAction('archive')}
        onCancel={() => setPending(null)}
      />
      <ConfirmDialog
        open={pending === 'restore'}
        title="Восстановить заказ?"
        description={<p>Заказ вернётся из архива с прежним статусом. Используется версия из карточки; при конфликте потребуется перечитать данные.</p>}
        confirmLabel="Восстановить"
        busy={actionBusy}
        onConfirm={() => runAction('restore')}
        onCancel={() => setPending(null)}
      />
      <ConfirmDialog
        open={pending === 'delete'}
        title="Удалить заказ навсегда?"
        description={<p>Действие необратимо: заказ, его теги и заметки будут удалены без возможности восстановления.</p>}
        confirmLabel="Удалить навсегда"
        danger
        busy={actionBusy}
        onConfirm={() => runAction('delete')}
        onCancel={() => setPending(null)}
      />
    </main>
  );
}

interface NotesSectionProps {
  orderId: string;
  notes: OrderNote[];
  loading: boolean;
  error: ApiRequestError | null;
  editable: boolean;
  onChanged: () => void;
  onRetry: () => void;
}

function NotesSection({ orderId, notes, loading, error, editable, onChanged, onRetry }: NotesSectionProps) {
  const [text, setText] = useState('');
  const [pinned, setPinned] = useState(false);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<ApiRequestError | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [editText, setEditText] = useState('');
  const [editPinned, setEditPinned] = useState(false);
  const pending = useRef(false);

  const ordered = [...notes].sort((left, right) => {
    if (left.pinned !== right.pinned) return left.pinned ? -1 : 1;
    return right.createdAt.localeCompare(left.createdAt);
  });

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (pending.current) return;
    if (!text.trim()) { setFormError(new ApiRequestError(400, 'Текст заметки не может быть пустым.')); return; }
    pending.current = true;
    setBusy(true);
    setFormError(null);
    try {
      await createNote(orderId, text.trim(), pinned);
      setText('');
      setPinned(false);
      onChanged();
    } catch (cause) {
      setFormError(cause as ApiRequestError);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  async function saveEdit(note: OrderNote) {
    if (!editText.trim()) { setFormError(new ApiRequestError(400, 'Текст заметки не может быть пустым.')); return; }
    setBusy(true);
    setFormError(null);
    try {
      await updateNote(orderId, note.id, editText.trim(), editPinned, note.version);
      setEditing(null);
      onChanged();
    } catch (cause) {
      setFormError(cause as ApiRequestError);
    } finally {
      setBusy(false);
    }
  }

  async function remove(note: OrderNote) {
    if (!window.confirm('Удалить заметку? Действие необратимо.')) return;
    setBusy(true);
    setFormError(null);
    try {
      await deleteNote(orderId, note.id);
      onChanged();
    } catch (cause) {
      setFormError(cause as ApiRequestError);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card notes" aria-labelledby="notes-title">
      <h2 id="notes-title">Заметки</h2>
      {error && (
        <div className="alert alert--error" role="alert">
          <p>{error.status === 404 ? 'Заметки недоступны для этого заказа.' : error.message}</p>
          <p><button type="button" className="button--secondary" onClick={onRetry}>Повторить загрузку</button></p>
        </div>
      )}
      {loading && <p role="status" className="muted">Загружаем заметки…</p>}
      {!loading && !error && ordered.length === 0 && <p className="muted">Заметок пока нет.</p>}
      {ordered.length > 0 && (
        <ul className="note-list">
          {ordered.map(note => (
            <li key={note.id} className={`note${note.pinned ? ' note--pinned' : ''}`}>
              <div className="note-header">
                <strong>{note.authorName}</strong>
                <span className="muted">{formatDateTime(note.createdAt)}</span>
                {note.pinned && <span className="badge badge--warning">Закреплена</span>}
              </div>
              {editing === note.id ? (
                <div className="note-editor">
                  <label htmlFor={`note-text-${note.id}`}>Текст заметки</label>
                  <textarea id={`note-text-${note.id}`} value={editText} maxLength={5000} rows={4}
                    onChange={event => setEditText(event.target.value)} disabled={busy} />
                  <label className="check-label">
                    <input type="checkbox" checked={editPinned} onChange={event => setEditPinned(event.target.checked)} disabled={busy} />
                    Закрепить заметку
                  </label>
                  <div className="actions">
                    <button type="button" onClick={() => saveEdit(note)} disabled={busy}>Сохранить</button>
                    <button type="button" className="button--secondary" onClick={() => setEditing(null)} disabled={busy}>Отмена</button>
                  </div>
                </div>
              ) : (
                <>
                  <p className="preserve-lines">{note.text}</p>
                  {note.updatedAt && note.updatedAt !== note.createdAt && (
                    <p className="muted small">Изменена {formatDateTime(note.updatedAt)}</p>
                  )}
                  {editable && (
                    <div className="actions">
                      <button type="button" className="button--secondary" disabled={busy}
                        onClick={() => { setEditing(note.id); setEditText(note.text); setEditPinned(note.pinned); }}>
                        Изменить
                      </button>
                      <button type="button" className="button--danger" disabled={busy} onClick={() => remove(note)}>Удалить</button>
                    </div>
                  )}
                </>
              )}
            </li>
          ))}
        </ul>
      )}
      {editable && (
        <form className="note-form" onSubmit={submit} aria-busy={busy}>
          <h3>Новая заметка</h3>
          {formError && (
            <div className="alert alert--error" role="alert">
              <p>{formError.message}</p>
              {!!formError.fieldErrors.length && <ul>{formError.fieldErrors.map(item => <li key={item.field}>{item.field}: {item.message}</li>)}</ul>}
              {formError.correlationId && <small>Код обращения: {formError.correlationId}</small>}
            </div>
          )}
          <label htmlFor="new-note">Текст</label>
          <textarea id="new-note" rows={4} maxLength={5000} value={text} onChange={event => setText(event.target.value)} disabled={busy} />
          <label className="check-label">
            <input type="checkbox" checked={pinned} onChange={event => setPinned(event.target.checked)} disabled={busy} />
            Закрепить заметку
          </label>
          <button type="submit" disabled={busy}>{busy ? 'Сохраняем…' : 'Добавить заметку'}</button>
        </form>
      )}
      {!editable && <p className="muted">Заметки меняют роли EDITOR и ADMIN. Просмотр доступен всем авторизованным.</p>}
    </section>
  );
}
