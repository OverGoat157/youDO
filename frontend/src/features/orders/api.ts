/** Вызовы REST заказов, справочника категорий и участников. */
import { apiFetch, apiDownload, jsonRequest } from '../../api/client';
import type {
  BulkAction, BulkResult, Category, OrderNote, OrderQuery, OrderResponse,
  OrderUpdate, OrderWrite, Page, Participant,
} from './types';

/** Параметры поиска: пустые значения не отправляются, условия соединяются через AND на сервере. */
export function buildOrderParams(query: OrderQuery, includePaging = true): URLSearchParams {
  const params = new URLSearchParams();
  const text: Array<[string, string]> = [
    ['q', query.q],
    ['categoryId', query.categoryId],
    ['status', query.status],
    ['urgent', query.urgent],
    ['priority', query.priority],
    ['tag', query.tag],
    ['archived', query.archived],
  ];
  for (const [key, value] of text) {
    const trimmed = value.trim();
    if (trimmed) params.set(key, trimmed);
  }
  if (query.budgetOp && query.budgetValue.trim()) {
    params.set('budgetOp', query.budgetOp);
    params.set('budgetValue', query.budgetValue.trim());
  }
  if (includePaging) {
    params.set('page', String(query.page));
    params.set('size', String(query.size));
  }
  params.set('sort', `${query.sortField},${query.sortDirection}`);
  return params;
}

export function listOrders(query: OrderQuery, signal?: AbortSignal): Promise<Page<OrderResponse>> {
  return apiFetch<Page<OrderResponse>>(`/api/orders?${buildOrderParams(query).toString()}`, { signal });
}

export function getOrder(id: string, signal?: AbortSignal): Promise<OrderResponse> {
  return apiFetch<OrderResponse>(`/api/orders/${encodeURIComponent(id)}`, { signal });
}

export function createOrder(input: OrderWrite): Promise<OrderResponse> {
  return apiFetch<OrderResponse>('/api/orders', jsonRequest('POST', input));
}

export function updateOrder(id: string, input: OrderUpdate): Promise<OrderResponse> {
  return apiFetch<OrderResponse>(`/api/orders/${encodeURIComponent(id)}`, jsonRequest('PUT', input));
}

export function archiveOrder(id: string, version: number): Promise<void> {
  return apiFetch<void>(`/api/orders/${encodeURIComponent(id)}?version=${version}`, { method: 'DELETE' });
}

export function restoreOrder(id: string, version: number): Promise<OrderResponse> {
  return apiFetch<OrderResponse>(`/api/orders/${encodeURIComponent(id)}/restore`, jsonRequest('POST', { version }));
}

/** Доступно только ADMIN и только при включённой настройке PERMANENT_DELETE_ENABLED. */
export function deleteOrderPermanently(id: string, version: number): Promise<void> {
  return apiFetch<void>(`/api/orders/${encodeURIComponent(id)}/permanent?version=${version}`, { method: 'DELETE' });
}

export function bulkOrders(input: BulkAction): Promise<BulkResult> {
  return apiFetch<BulkResult>('/api/orders/bulk', jsonRequest('POST', input));
}

export function listNotes(orderId: string, signal?: AbortSignal): Promise<OrderNote[]> {
  return apiFetch<OrderNote[]>(`/api/orders/${encodeURIComponent(orderId)}/notes`, { signal });
}

export function createNote(orderId: string, text: string, pinned: boolean): Promise<OrderNote> {
  return apiFetch<OrderNote>(`/api/orders/${encodeURIComponent(orderId)}/notes`, jsonRequest('POST', { text, pinned }));
}

export function updateNote(orderId: string, noteId: string, text: string, pinned: boolean, version?: number): Promise<OrderNote> {
  return apiFetch<OrderNote>(
    `/api/orders/${encodeURIComponent(orderId)}/notes/${encodeURIComponent(noteId)}`,
    jsonRequest('PUT', version === undefined ? { text, pinned } : { text, pinned, version }),
  );
}

export function deleteNote(orderId: string, noteId: string): Promise<void> {
  return apiFetch<void>(
    `/api/orders/${encodeURIComponent(orderId)}/notes/${encodeURIComponent(noteId)}`,
    { method: 'DELETE' },
  );
}

export function listCategories(signal?: AbortSignal): Promise<Category[]> {
  return apiFetch<Category[]>('/api/categories', { signal });
}

export interface ParticipantQuery {
  role?: 'CUSTOMER' | 'FREELANCER';
  q?: string;
  page?: number;
  size?: number;
}

export function listParticipants(query: ParticipantQuery = {}, signal?: AbortSignal): Promise<Page<Participant>> {
  const params = new URLSearchParams();
  if (query.role) params.set('role', query.role);
  if (query.q?.trim()) params.set('q', query.q.trim());
  params.set('page', String(query.page ?? 0));
  params.set('size', String(query.size ?? 20));
  return apiFetch<Page<Participant>>(`/api/participants?${params.toString()}`, { signal });
}

/** Экспорт по тем же условиям; модуль CSV принадлежит Сергею, кнопка — здесь. */
export function exportOrders(query: OrderQuery): Promise<{ blob: Blob; filename: string | null }> {
  return apiDownload(`/api/orders/export?${buildOrderParams(query, false).toString()}`);
}
