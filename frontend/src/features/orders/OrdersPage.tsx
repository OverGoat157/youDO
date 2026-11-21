import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { linkProps } from '../../app/router';
import { ApiRequestError } from '../../api/client';
import { canEdit, type Account } from '../../api/session';
import { archivedFilterLabel } from './labels';
import {
  bulkOrders, exportOrders, listCategories, listOrders,
} from './api';
import {
  EMPTY_FILTER, STATUS_LABELS, PRIORITY_LABELS, formatBudget, formatDeadline,
  normalizeTags, type BulkResult, type Category, type OrderFilter, type OrderResponse,
  type OrderStatus, type Page, type SortDirection, type SortField,
} from './types';
import { Pager, PriorityBadge, StatusBadge } from './components';
import './orders.css';

export interface OrdersPageProps {
  user: Account;
}

const SORTABLE: Array<{ field: SortField; label: string }> = [
  { field: 'title', label: 'Название' },
  { field: 'budget', label: 'Бюджет' },
  { field: 'deadline', label: 'Дедлайн' },
];

type BulkKind = 'ARCHIVE' | 'RESTORE' | 'CHANGE_STATUS' | 'ADD_TAG' | 'REMOVE_TAG';

function saveBlob(blob: Blob, filename: string | null): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename ?? 'orders.csv';
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

export function OrdersPage({ user }: OrdersPageProps) {
  const [draft, setDraft] = useState<OrderFilter>(EMPTY_FILTER);
  const [filter, setFilter] = useState<OrderFilter>(EMPTY_FILTER);
  const [categories, setCategories] = useState<Category[]>([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sortField, setSortField] = useState<SortField>('createdAt');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<Page<OrderResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiRequestError | Error | null>(null);
  const [notice, setNotice] = useState<{ kind: 'success' | 'error' | 'info'; text: string } | null>(null);
  const [selection, setSelection] = useState<Map<string, number>>(new Map());
  const [bulkKind, setBulkKind] = useState<BulkKind>('ARCHIVE');
  const [bulkStatus, setBulkStatus] = useState<OrderStatus>('CANCELLED');
  const [bulkTag, setBulkTag] = useState('');
  const [bulkBusy, setBulkBusy] = useState(false);
  const [bulkResult, setBulkResult] = useState<BulkResult | null>(null);
  const [exportBusy, setExportBusy] = useState(false);
  const exportRef = useRef<HTMLButtonElement>(null);
  const editable = canEdit(user);

  useEffect(() => {
    const controller = new AbortController();
    listCategories(controller.signal)
      .then(setCategories)
      .catch(() => setCategories([]));
    return () => controller.abort();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    listOrders({ ...filter, page, size, sortField, sortDirection }, controller.signal)
      .then(data => {
        setResult(data);

        setSelection(previous => {
          if (previous.size === 0) return previous;
          const next = new Map<string, number>();
          for (const order of data.content) {
            if (previous.has(order.id)) next.set(order.id, order.version);
          }
          return next;
        });
      })
      .catch(cause => {
        if (cause instanceof DOMException && cause.name === 'AbortError') return;
        setError(cause as Error);
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [filter, page, size, sortField, sortDirection, revision]);

  const query = useMemo(
    () => ({ ...filter, page, size, sortField, sortDirection }),
    [filter, page, size, sortField, sortDirection],
  );

  const applyFilters = useCallback((event?: FormEvent) => {
    event?.preventDefault();
    setFilter(draft);
    setPage(0);
    setSelection(new Map());
  }, [draft]);

  const resetFilters = useCallback(() => {
    setDraft(EMPTY_FILTER);
    setFilter(EMPTY_FILTER);
    setPage(0);
    setSelection(new Map());
  }, []);

  function toggleSort(field: SortField) {
    if (field === sortField) {
      setSortDirection(current => (current === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDirection(field === 'title' ? 'asc' : 'desc');
    }
    setPage(0);
  }

  function toggleRow(order: OrderResponse, checked: boolean) {
    setSelection(previous => {
      const next = new Map(previous);
      if (checked) next.set(order.id, order.version);
      else next.delete(order.id);
      return next;
    });
  }

  const pageRows = result?.content ?? [];
  const allSelected = pageRows.length > 0 && pageRows.every(order => selection.has(order.id));

  function toggleAll(checked: boolean) {
    setSelection(previous => {
      const next = new Map(previous);
      for (const order of pageRows) {
        if (checked) next.set(order.id, order.version);
        else next.delete(order.id);
      }
      return next;
    });
  }

  async function runExport() {
    setExportBusy(true);
    setNotice(null);
    try {
      const { blob, filename } = await exportOrders(query);
      saveBlob(blob, filename);
      setNotice({ kind: 'success', text: 'Экспорт текущих условий скачан.' });
    } catch (cause) {
      const api = cause as ApiRequestError;
      setNotice({
        kind: 'error',
        text: api.status === 404 ? 'Экспорт сейчас недоступен.' : api.message || 'Не удалось выгрузить CSV.',
      });
    } finally {
      setExportBusy(false);
      requestAnimationFrame(() => exportRef.current?.focus());
    }
  }

  async function runBulk(event: FormEvent) {
    event.preventDefault();
    if (selection.size === 0 || bulkBusy) return;
    if ((bulkKind === 'ADD_TAG' || bulkKind === 'REMOVE_TAG') && normalizeTags(bulkTag).length === 0) {
      setNotice({ kind: 'error', text: 'Укажите тег для массовой операции.' });
      return;
    }
    setBulkBusy(true);
    setBulkResult(null);
    setNotice(null);
    try {
      const payload = {
        action: bulkKind,
        items: Array.from(selection, ([id, version]) => ({ id, version })),
        ...(bulkKind === 'CHANGE_STATUS' ? { status: bulkStatus } : {}),
        ...(bulkKind === 'ADD_TAG' || bulkKind === 'REMOVE_TAG' ? { tag: normalizeTags(bulkTag)[0] } : {}),
      };
      const response = await bulkOrders(payload);
      setBulkResult(response);
      setSelection(new Map(response.failed.map(item => [item.id, selection.get(item.id) ?? 0])));
      setRevision(value => value + 1);
      setNotice(response.failed.length === 0
        ? { kind: 'success', text: `Выполнено для ${response.succeeded.length} заказов.` }
        : {
          kind: 'error',
          text: `Успешно: ${response.succeeded.length}, с ошибкой: ${response.failed.length}. Ошибочные строки остались выбранными.`,
        });
    } catch (cause) {
      const api = cause as ApiRequestError;
      setNotice({ kind: 'error', text: api.message || 'Массовая операция не выполнена.' });
    } finally {
      setBulkBusy(false);
    }
  }

  const filtersActive = Object.entries(filter).some(([key, value]) =>
    key === 'archived' ? value !== 'false' : String(value).trim() !== '');

  return (
    <main className="page" aria-labelledby="orders-title">
      <header className="page-header">
        <div>
          <h1 id="orders-title">Заказы</h1>
          <p className="muted">Заказы биржи: поиск, фильтры и сортировка</p>
        </div>
        <div className="actions">
          <button type="button" className="button--secondary" onClick={() => setRevision(value => value + 1)} disabled={loading}>Обновить</button>
          <button type="button" className="button--secondary" ref={exportRef} onClick={runExport} disabled={exportBusy}>
            {exportBusy ? 'Выгружаем…' : 'Экспорт CSV'}
          </button>
          {editable && <a className="button" {...linkProps('/orders/new')}>Создать заказ</a>}
        </div>
      </header>

      {notice && (
        <div className={`alert ${notice.kind === 'success' ? 'alert--success' : notice.kind === 'error' ? 'alert--error' : ''}`} role={notice.kind === 'error' ? 'alert' : 'status'}>
          <p>{notice.text}</p>
        </div>
      )}

      <form className="card filters" onSubmit={applyFilters} aria-label="Фильтры заказов">
        <div className="filters-grid">
          <div className="field field--wide">
            <label htmlFor="filter-q">Поиск</label>
            <input id="filter-q" value={draft.q} maxLength={150} placeholder="Название, описание или код"
              onChange={event => setDraft({ ...draft, q: event.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="filter-category">Категория</label>
            <select id="filter-category" value={draft.categoryId} onChange={event => setDraft({ ...draft, categoryId: event.target.value })}>
              <option value="">Все категории</option>
              {categories.map(category => (
                <option key={category.id} value={category.id}>
                  {category.name}{category.active ? '' : ' (отключена)'}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="filter-status">Статус</label>
            <select id="filter-status" value={draft.status} onChange={event => setDraft({ ...draft, status: event.target.value })}>
              <option value="">Любой статус</option>
              {(Object.keys(STATUS_LABELS) as OrderStatus[]).map(status => (
                <option key={status} value={status}>{STATUS_LABELS[status]}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="filter-priority">Приоритет</label>
            <select id="filter-priority" value={draft.priority} onChange={event => setDraft({ ...draft, priority: event.target.value })}>
              <option value="">Любой приоритет</option>
              {(Object.keys(PRIORITY_LABELS) as Array<keyof typeof PRIORITY_LABELS>).map(priority => (
                <option key={priority} value={priority}>{PRIORITY_LABELS[priority]}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="filter-urgent">Срочность</label>
            <select id="filter-urgent" value={draft.urgent} onChange={event => setDraft({ ...draft, urgent: event.target.value })}>
              <option value="">Все</option>
              <option value="true">Только срочные</option>
              <option value="false">Без срочности</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="filter-tag">Тег</label>
            <input id="filter-tag" value={draft.tag} maxLength={50} placeholder="например, лендинг"
              onChange={event => setDraft({ ...draft, tag: event.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="filter-archived">Архив</label>
            <select id="filter-archived" value={draft.archived}
              onChange={event => setDraft({ ...draft, archived: event.target.value as OrderFilter['archived'] })}>
              <option value="false">{archivedFilterLabel('false')}</option>
              <option value="true">{archivedFilterLabel('true')}</option>
              <option value="all">{archivedFilterLabel('all')}</option>
            </select>
          </div>
          <div className="field field--budget">
            <label htmlFor="filter-budget-from">Бюджет, ₽</label>
            <div className="budget-range">
              <input id="filter-budget-from" inputMode="decimal" placeholder="от" aria-label="Бюджет от"
                value={draft.budgetFrom}
                onChange={event => setDraft({ ...draft, budgetFrom: event.target.value })} />
              <span className="budget-dash" aria-hidden="true">—</span>
              <input id="filter-budget-to" inputMode="decimal" placeholder="до" aria-label="Бюджет до"
                value={draft.budgetTo}
                onChange={event => setDraft({ ...draft, budgetTo: event.target.value })} />
            </div>
          </div>
        </div>
        <div className="actions">
          <button type="submit">Найти</button>
          <button type="button" className="button--secondary" onClick={resetFilters} disabled={!filtersActive && !Object.values(draft).some(value => value !== '' && value !== 'false')}>Сбросить</button>
          <span className="muted">Условия сохраняются при обновлении и переходе между страницами</span>
        </div>
      </form>

      {error && (
        <div className="alert alert--error" role="alert">
          <p>{error.message || 'Не удалось загрузить заказы.'}</p>
          {(error as ApiRequestError).correlationId && <small>Код обращения: {(error as ApiRequestError).correlationId}</small>}
          <p><button type="button" className="button--secondary" onClick={() => setRevision(value => value + 1)}>Повторить загрузку</button></p>
        </div>
      )}

      {editable && selection.size > 0 && (
        <form className="card bulk-panel" onSubmit={runBulk} aria-label="Массовые действия">
          <h2>Выбрано заказов: {selection.size}</h2>
          <div className="actions bulk-row">
            <div className="field">
              <label htmlFor="bulk-action">Действие</label>
              <select id="bulk-action" value={bulkKind} onChange={event => setBulkKind(event.target.value as BulkKind)} disabled={bulkBusy}>
                <option value="ARCHIVE">В архив</option>
                <option value="RESTORE">Восстановить из архива</option>
                <option value="CHANGE_STATUS">Сменить статус</option>
                <option value="ADD_TAG">Добавить тег</option>
                <option value="REMOVE_TAG">Удалить тег</option>
              </select>
            </div>
            {bulkKind === 'CHANGE_STATUS' && (
              <div className="field">
                <label htmlFor="bulk-status">Новый статус</label>
                <select id="bulk-status" value={bulkStatus} onChange={event => setBulkStatus(event.target.value as OrderStatus)} disabled={bulkBusy}>
                  {(Object.keys(STATUS_LABELS) as OrderStatus[]).map(status => (
                    <option key={status} value={status}>{STATUS_LABELS[status]}</option>
                  ))}
                </select>
              </div>
            )}
            {(bulkKind === 'ADD_TAG' || bulkKind === 'REMOVE_TAG') && (
              <div className="field">
                <label htmlFor="bulk-tag">Тег</label>
                <input id="bulk-tag" value={bulkTag} maxLength={50} onChange={event => setBulkTag(event.target.value)} disabled={bulkBusy} />
              </div>
            )}
            <button type="submit" disabled={bulkBusy}>{bulkBusy ? 'Выполняем…' : 'Применить к выбранным'}</button>
            <button type="button" className="button--secondary" disabled={bulkBusy} onClick={() => { setSelection(new Map()); setBulkResult(null); }}>Снять выбор</button>
          </div>
          {bulkResult && (
            <div className="bulk-result" role="status">
              <p><strong>Успешно:</strong> {bulkResult.succeeded.length} · <strong>С ошибкой:</strong> {bulkResult.failed.length}</p>
              {bulkResult.failed.length > 0 && (
                <ul>
                  {bulkResult.failed.map(item => (
                    <li key={item.id}>{orderCode(bulkResult, item.id, pageRows)} — {item.code}: {item.message}</li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </form>
      )}

      {loading && <p role="status" className="muted">Загружаем заказы…</p>}

      {!loading && !error && pageRows.length === 0 && (
        <p className="empty-state">
          {filtersActive ? 'По заданным условиям заказов нет. Измените фильтры или сбросьте их.' : 'Заказов пока нет. Создайте первый заказ.'}
        </p>
      )}

      {pageRows.length > 0 && (
        <>
          <div className="table-scroll" role="region" aria-label="Таблица заказов" tabIndex={0} aria-busy={loading}>
            <table>
              <caption className="visually-hidden">Заказы с учётом выбранных условий</caption>
              <thead>
                <tr>
                  {editable && (
                    <th scope="col" className="cell-select">
                      <input type="checkbox" checked={allSelected} aria-label="Выбрать все заказы на странице"
                        onChange={event => toggleAll(event.target.checked)} />
                    </th>
                  )}
                  {SORTABLE.map(column => (
                    <th key={column.field} scope="col" aria-sort={
                      sortField === column.field ? (sortDirection === 'asc' ? 'ascending' : 'descending') : 'none'
                    }>
                      <button type="button" className="sort-button" onClick={() => toggleSort(column.field)}>
                        {column.label}
                        {sortField === column.field && <span aria-hidden="true">{sortDirection === 'asc' ? ' ↑' : ' ↓'}</span>}
                      </button>
                    </th>
                  ))}
                  <th scope="col">Категория</th>
                  <th scope="col">Статус</th>
                  <th scope="col">Заказчик / исполнитель</th>
                  <th scope="col">Приоритет</th>
                </tr>
              </thead>
              <tbody>
                {pageRows.map(order => (
                  <tr key={order.id} className={selection.has(order.id) ? 'row-selected' : undefined}>
                    {editable && (
                      <td className="cell-select">
                        <input type="checkbox" checked={selection.has(order.id)}
                          aria-label={`Выбрать заказ ${order.code}`}
                          onChange={event => toggleRow(order, event.target.checked)} />
                      </td>
                    )}
                    <td className="cell-title">
                      <a className="title-link" {...linkProps(`/orders/${order.id}`)}>{order.title}</a>
                      <div className="subline">
                        <span className="mono">{order.code}</span>
                        {order.archived && <span className="badge badge--inactive">В архиве</span>}
                      </div>
                    </td>
                    <td className="nowrap">{formatBudget(order.budget)}</td>
                    <td className="nowrap">{formatDeadline(order.deadline)}</td>
                    <td>{order.categoryName ?? '—'}</td>
                    <td className="nowrap"><StatusBadge status={order.status} /></td>
                    <td>
                      <div className="nowrap">{order.customerName}</div>
                      <div className="muted small">{order.freelancerName ?? 'исполнитель не назначен'}</div>
                    </td>
                    <td>
                      <div className="priority-cell">
                        <PriorityBadge priority={order.priority} />
                        {order.urgent && <span className="badge badge--overdue">Срочный</span>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {result && (
            <Pager
              page={result.page}
              totalPages={result.totalPages}
              totalElements={result.totalElements}
              size={result.size}
              disabled={loading}
              onPage={setPage}
              onSize={value => { setSize(value); setPage(0); }}
            />
          )}
          <p className="visually-hidden" aria-live="polite">{selection.size > 0 ? `Выбрано заказов: ${selection.size}` : ''}</p>
        </>
      )}

    </main>
  );
}

function orderCode(result: BulkResult, id: string, rows: OrderResponse[]): string {
  const row = rows.find(item => item.id === id);
  if (row) return row.code;
  return result.failed.find(item => item.id === id)?.id.slice(0, 8) ?? id.slice(0, 8);
}
