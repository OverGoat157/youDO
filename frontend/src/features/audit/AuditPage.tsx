import { useEffect, useState, type FormEvent } from 'react';
import type { ApiRequestError } from '../../api/client';
import { ErrorAlert } from '../../app/ui';
import { formatDateTime, type Page } from '../orders/types';
import {
  ACTION_LABELS, EMPTY_AUDIT_FILTER, ENTITY_LABELS, listAudit,
  type AuditEvent, type AuditFilter, type AuditSort,
} from './api';
import './audit.css';

const SORT_OPTIONS: Array<{ value: AuditSort; label: string }> = [
  { value: 'occurredAt,desc', label: 'Сначала новые' },
  { value: 'occurredAt,asc', label: 'Сначала старые' },
  { value: 'action,asc', label: 'По действию' },
  { value: 'actorLogin,asc', label: 'По пользователю' },
  { value: 'entityType,asc', label: 'По типу объекта' },
];

/** Журнал аудита: только для ADMIN (маршрут проверяет роль, сервер — тоже). */
export function AuditPage() {
  const [draft, setDraft] = useState<AuditFilter>(EMPTY_AUDIT_FILTER);
  const [filter, setFilter] = useState<AuditFilter>(EMPTY_AUDIT_FILTER);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState<AuditSort>('occurredAt,desc');
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<Page<AuditEvent> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiRequestError | Error | null>(null);

  useEffect(() => {
    // Новый запрос отменяет предыдущий: ответ на старые условия не перепишет таблицу
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    listAudit({ ...filter, page, size, sort }, controller.signal)
      .then(setResult)
      .catch(cause => {
        if (cause instanceof DOMException && cause.name === 'AbortError') return;
        setError(cause as ApiRequestError);
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [filter, page, size, sort, revision]);

  function apply(event: FormEvent) {
    event.preventDefault();
    if (draft.from && draft.to && draft.from >= draft.to) {
      setError(new Error('Начало периода должно быть раньше конца.'));
      return;
    }
    setFilter(draft);
    setPage(0);
  }

  function reset() {
    setDraft(EMPTY_AUDIT_FILTER);
    setFilter(EMPTY_AUDIT_FILTER);
    setPage(0);
  }

  const events = result?.content ?? [];
  const totalPages = Math.max(1, result?.totalPages ?? 1);

  return (
    <main className="page" aria-labelledby="audit-title">
      <header className="page-header">
        <div>
          <h1 id="audit-title">Журнал аудита</h1>
          <p className="muted">Входы и изменения данных: кто, что и когда сделал</p>
        </div>
        <button type="button" className="button--secondary" disabled={loading} onClick={() => setRevision(value => value + 1)}>
          Обновить
        </button>
      </header>

      <form className="card audit-filters" onSubmit={apply} aria-label="Фильтры журнала">
        <div className="audit-filters__grid">
          <div className="audit-field audit-field--wide">
            <label htmlFor="audit-q">Поиск</label>
            <input id="audit-q" value={draft.q} maxLength={200} placeholder="Логин, описание, код действия или correlation ID"
              onChange={event => setDraft({ ...draft, q: event.target.value })} />
          </div>
          <div className="audit-field">
            <label htmlFor="audit-action">Действие</label>
            <input id="audit-action" list="audit-actions" value={draft.action} maxLength={60} placeholder="Любое"
              onChange={event => setDraft({ ...draft, action: event.target.value })} />
            <datalist id="audit-actions">
              {Object.entries(ACTION_LABELS).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
            </datalist>
          </div>
          <div className="audit-field">
            <label htmlFor="audit-entity">Объект</label>
            <select id="audit-entity" value={draft.entityType} onChange={event => setDraft({ ...draft, entityType: event.target.value })}>
              <option value="">Любой</option>
              {Object.entries(ENTITY_LABELS).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
            </select>
          </div>
          <div className="audit-field">
            <label htmlFor="audit-from">С</label>
            <input id="audit-from" type="datetime-local" value={draft.from}
              onChange={event => setDraft({ ...draft, from: event.target.value })} />
          </div>
          <div className="audit-field">
            <label htmlFor="audit-to">По</label>
            <input id="audit-to" type="datetime-local" value={draft.to}
              onChange={event => setDraft({ ...draft, to: event.target.value })} />
          </div>
        </div>
        <div className="actions">
          <button type="submit" disabled={loading}>Найти</button>
          <button type="button" className="button--secondary" onClick={reset}>Сбросить</button>
          <label className="audit-sort">
            Сортировка
            <select value={sort} onChange={event => { setSort(event.target.value as AuditSort); setPage(0); }}>
              {SORT_OPTIONS.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>
        </div>
      </form>

      <ErrorAlert error={error} onRetry={() => setRevision(value => value + 1)} />
      {loading && <p role="status" className="muted">Загружаем журнал…</p>}
      {!loading && !error && events.length === 0 && <p className="empty-state">Записей по этим условиям нет.</p>}

      {events.length > 0 && (
        <div className="table-scroll" role="region" aria-label="Записи журнала аудита" tabIndex={0}>
          <table>
            <caption className="visually-hidden">Журнал аудита</caption>
            <thead>
              <tr>
                <th scope="col">Время</th>
                <th scope="col">Пользователь</th>
                <th scope="col">Действие</th>
                <th scope="col">Объект</th>
                <th scope="col">Описание</th>
                <th scope="col">Correlation ID</th>
              </tr>
            </thead>
            <tbody>
              {events.map(event => (
                <tr key={event.id}>
                  <td className="nowrap">{formatDateTime(event.occurredAt)}</td>
                  <td>{event.actorLogin ?? <span className="muted">не определён</span>}</td>
                  <td>
                    {ACTION_LABELS[event.action] ?? event.action}
                    <div className="mono muted small">{event.action}</div>
                  </td>
                  <td>
                    {ENTITY_LABELS[event.entityType] ?? event.entityType}
                    {event.entityId && <div className="mono muted small" title={event.entityId}>{event.entityId.slice(0, 8)}…</div>}
                  </td>
                  <td>{event.summary}</td>
                  <td className="mono small">{event.correlationId ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {result && (
        <nav className="pagination audit-pagination" aria-label="Страницы журнала">
          <button type="button" className="button--secondary" disabled={loading || page <= 0} onClick={() => setPage(page - 1)}>Назад</button>
          <span aria-live="polite">Страница {page + 1} из {totalPages} · Записей {result.totalElements}</span>
          <button type="button" className="button--secondary" disabled={loading || page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Далее</button>
          <label className="audit-sort">
            На странице
            <select value={size} onChange={event => { setSize(Number(event.target.value)); setPage(0); }}>
              {[10, 20, 50, 100].map(value => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
        </nav>
      )}
    </main>
  );
}
