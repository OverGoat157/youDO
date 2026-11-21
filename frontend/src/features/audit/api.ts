import { apiFetch } from '../../api/client';
import type { Page } from '../orders/types';

export interface AuditEvent {
  id: string;
  actorId: string | null;
  actorLogin: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  occurredAt: string;
  summary: string;
  correlationId: string | null;
}

export type AuditSort = 'occurredAt,desc' | 'occurredAt,asc' | 'action,asc' | 'entityType,asc' | 'actorLogin,asc';

export interface AuditFilter {
  q: string;
  action: string;
  entityType: string;
  /** Значение поля datetime-local (местное время) или пустая строка. */
  from: string;
  to: string;
}

export interface AuditQuery extends AuditFilter {
  page: number;
  size: number;
  sort: AuditSort;
}

export const EMPTY_AUDIT_FILTER: AuditFilter = { q: '', action: '', entityType: '', from: '', to: '' };

export const ACTION_LABELS: Record<string, string> = {
  LOGIN: 'Вход',
  LOGIN_FAILED: 'Неудачный вход',
  LOGOUT: 'Выход',
  PASSWORD_CHANGED: 'Смена пароля',
  ACCOUNT_CREATED: 'Создан пользователь',
  ACCOUNT_UPDATED: 'Изменён пользователь',
  ACCOUNT_PASSWORD_CHANGED: 'Пароль пользователя изменён',
  ACCOUNT_PASSWORD_RESET: 'Сброс пароля',
  CATEGORY_CREATED: 'Создана категория',
  CATEGORY_UPDATED: 'Изменена категория',
  ORDER_CREATED: 'Создан заказ',
  ORDER_UPDATED: 'Изменён заказ',
  ORDER_ARCHIVED: 'Заказ в архиве',
  ORDER_RESTORED: 'Заказ восстановлен',
  ORDER_DELETED: 'Заказ удалён навсегда',
  ORDER_STATUS_CHANGED: 'Изменён статус заказа',
  ORDER_TAGS_CHANGED: 'Изменены теги заказа',
  ORDER_NOTE_CREATED: 'Добавлена заметка',
  ORDER_NOTE_UPDATED: 'Изменена заметка',
  ORDER_NOTE_DELETED: 'Удалена заметка',
  ORDER_CSV_IMPORTED: 'Импорт CSV',
};

export const ENTITY_LABELS: Record<string, string> = {
  ACCOUNT: 'Учётная запись',
  CATEGORY: 'Категория',
  ORDER: 'Заказ',
  ORDER_NOTE: 'Заметка заказа',
};

/** Местное время из поля формы → ISO в UTC, как ждёт сервер. */
export function toIsoUtc(local: string): string {
  return new Date(local).toISOString();
}

export function buildAuditParams(query: AuditQuery): URLSearchParams {
  const params = new URLSearchParams();
  for (const key of ['q', 'action', 'entityType'] as const) {
    const value = query[key].trim();
    if (value) params.set(key, value);
  }
  if (query.from) params.set('from', toIsoUtc(query.from));
  if (query.to) params.set('to', toIsoUtc(query.to));
  params.set('page', String(query.page));
  params.set('size', String(query.size));
  params.set('sort', query.sort);
  return params;
}

export function listAudit(query: AuditQuery, signal?: AbortSignal): Promise<Page<AuditEvent>> {
  return apiFetch<Page<AuditEvent>>(`/api/admin/audit?${buildAuditParams(query).toString()}`, { signal });
}
