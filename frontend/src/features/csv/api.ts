import { apiDownload, apiFetch, jsonRequest } from '../../api/client';
import type { ArchivedFilter } from '../orders/types';

export type ImportMode = 'CREATE_ONLY' | 'UPSERT_BY_CODE';

export interface RowIssue {
  line: number;
  field: string;
  message: string;
}

export interface ImportPreview {
  previewId: string;
  mode: ImportMode;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  toCreate: number;
  toUpdate: number;
  issues: RowIssue[];
  expiresAt: string;
}

export interface ImportResult {
  created: number;
  updated: number;
  skipped: number;
}

export const MODE_LABELS: Record<ImportMode, string> = {
  CREATE_ONLY: 'Только новые',
  UPSERT_BY_CODE: 'Новые и обновление по коду',
};

/** Колонки файла: такие же пишет экспорт, поэтому выгруженный файл можно поправить и загрузить обратно. */
export const CSV_COLUMNS: Array<{ name: string; hint: string }> = [
  { name: 'code', hint: 'внешний код заказа, уникальный; по нему идёт обновление' },
  { name: 'title', hint: 'название, до 150 символов' },
  { name: 'description', hint: 'описание, можно пустым' },
  { name: 'category', hint: 'код категории, например DEVELOPMENT' },
  { name: 'status', hint: 'OPEN, IN_PROGRESS, ON_REVIEW, COMPLETED или CANCELLED' },
  { name: 'budget', hint: 'бюджет, например 15000.00 или 15 000,00' },
  { name: 'deadline', hint: 'дедлайн ГГГГ-ММ-ДД или ДД.ММ.ГГГГ' },
  { name: 'customerEmail', hint: 'email заказчика' },
  { name: 'freelancerEmail', hint: 'email исполнителя, можно пустым' },
  { name: 'urgent', hint: 'true/false или да/нет' },
  { name: 'tags', hint: 'теги через |, например java|веб' },
];

export function previewImport(file: File, mode: ImportMode): Promise<ImportPreview> {
  const body = new FormData();
  body.append('file', file);
  body.append('mode', mode);
  return apiFetch<ImportPreview>('/api/orders/import/preview', { method: 'POST', body });
}

export function applyImport(previewId: string): Promise<ImportResult> {
  return apiFetch<ImportResult>('/api/orders/import/apply', jsonRequest('POST', { previewId }));
}

export function exportOrdersCsv(archived: ArchivedFilter): Promise<{ blob: Blob; filename: string | null }> {
  return apiDownload(`/api/orders/export?archived=${archived}&sort=createdAt,desc`);
}
