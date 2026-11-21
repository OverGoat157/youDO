import { useEffect, useRef } from 'react';
import {
  PRIORITY_LABELS, STATUS_LABELS, statusClass,
  type OrderPriority, type OrderStatus,
} from './types';

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`badge badge--${statusClass(status)}`}>{STATUS_LABELS[status] ?? status}</span>;
}

export function PriorityBadge({ priority }: { priority: OrderPriority }) {
  return <span className={`badge badge--${priority.toLowerCase()}`}>{PRIORITY_LABELS[priority] ?? priority}</span>;
}

export function ArchivedBadge({ archived }: { archived: boolean }) {
  return archived
    ? <span className="badge badge--inactive">В архиве</span>
    : <span className="badge badge--active">Активен</span>;
}

export interface PagerProps {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  disabled?: boolean;
  onPage: (page: number) => void;
  onSize: (size: number) => void;
}

export function Pager({ page, totalPages, totalElements, size, disabled, onPage, onSize }: PagerProps) {
  const lastPage = Math.max(1, totalPages);
  return (
    <nav className="pagination" aria-label="Страницы заказов">
      <button type="button" className="button--secondary" disabled={disabled || page <= 0} onClick={() => onPage(page - 1)}>Назад</button>
      <span aria-live="polite">Страница {page + 1} из {lastPage} · Найдено {totalElements}</span>
      <button type="button" className="button--secondary" disabled={disabled || page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Далее</button>
      <label className="pager-size">
        На странице
        <select value={size} disabled={disabled} onChange={event => onSize(Number(event.target.value))}>
          {[10, 20, 50, 100].map(value => <option key={value} value={value}>{value}</option>)}
        </select>
      </label>
    </nav>
  );
}

export interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: React.ReactNode;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({ open, title, description, confirmLabel, danger, busy, onConfirm, onCancel }: ConfirmDialogProps) {
  const heading = useRef<HTMLHeadingElement>(null);
  const cancel = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (open) cancel.current?.focus();
  }, [open]);
  useEffect(() => {
    if (!open) return;
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') onCancel();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onCancel]);
  if (!open) return null;
  return (
    <div className="dialog-backdrop" role="presentation" onClick={event => { if (event.target === event.currentTarget) onCancel(); }}>
      <section className="card dialog" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
        <h2 id="confirm-title" ref={heading}>{title}</h2>
        <div>{description}</div>
        <div className="actions">
          <button type="button" className={danger ? 'button--danger' : undefined} disabled={busy} onClick={onConfirm}>
            {busy ? 'Выполняем…' : confirmLabel}
          </button>
          <button type="button" className="button--secondary" ref={cancel} disabled={busy} onClick={onCancel}>Отмена</button>
        </div>
      </section>
    </div>
  );
}
