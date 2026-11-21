export type OrderStatus = 'OPEN' | 'IN_PROGRESS' | 'ON_REVIEW' | 'COMPLETED' | 'CANCELLED';
export type OrderPriority = 'LOW' | 'MEDIUM' | 'HIGH';
export type ArchivedFilter = 'false' | 'true' | 'all';
export type SortField = 'title' | 'budget' | 'deadline' | 'createdAt';
export type SortDirection = 'asc' | 'desc';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Category {
  id: string;
  code: string;
  name: string;
  active: boolean;
}

export type ParticipantId = number | string;

export interface Participant {
  id: ParticipantId;
  name: string;
  email: string;
  role?: 'CUSTOMER' | 'FREELANCER';
}

export interface OrderResponse {
  id: string;
  code: string;
  title: string;
  description: string;
  categoryId: string | null;
  categoryName: string | null;
  status: OrderStatus;
  budget: string;
  deadline: string;
  customerId: ParticipantId;
  customerName: string;
  freelancerId: ParticipantId | null;
  freelancerName: string | null;
  urgent: boolean;
  tags: string[];
  priority: OrderPriority;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface OrderWrite {
  code: string;
  title: string;
  description: string;
  categoryId: string;
  status?: OrderStatus;
  budget: string;
  deadline: string;
  customerId: ParticipantId;
  freelancerId: ParticipantId | null;
  urgent: boolean;
  tags: string[];
}

export interface OrderUpdate extends OrderWrite {
  version: number;
}

export interface OrderNote {
  id: string;
  text: string;
  pinned: boolean;
  authorId: string;
  authorName: string;
  createdAt: string;
  updatedAt: string;
  version?: number;
}

export interface BulkAction {
  action: 'ARCHIVE' | 'RESTORE' | 'CHANGE_STATUS' | 'ADD_TAG' | 'REMOVE_TAG';
  items: Array<{ id: string; version: number }>;
  status?: OrderStatus;
  tag?: string;
}

export interface BulkResult {
  succeeded: Array<{ id: string; version: number }>;
  failed: Array<{ id: string; code: string; message: string }>;
}

export interface OrderFilter {
  q: string;
  categoryId: string;
  status: string;
  urgent: string;
  priority: string;
  tag: string;
  archived: ArchivedFilter;
  budgetFrom: string;
  budgetTo: string;
}

export interface OrderQuery extends OrderFilter {
  page: number;
  size: number;
  sortField: SortField;
  sortDirection: SortDirection;
}

export const EMPTY_FILTER: OrderFilter = {
  q: '', categoryId: '', status: '', urgent: '', priority: '', tag: '',
  archived: 'false', budgetFrom: '', budgetTo: '',
};

export const STATUS_LABELS: Record<OrderStatus, string> = {
  OPEN: 'Открыт',
  IN_PROGRESS: 'В работе',
  ON_REVIEW: 'На проверке',
  COMPLETED: 'Завершён',
  CANCELLED: 'Отменён',
};

export const PRIORITY_LABELS: Record<OrderPriority, string> = {
  LOW: 'Низкий',
  MEDIUM: 'Средний',
  HIGH: 'Высокий',
};

export const STATUS_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  OPEN: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['ON_REVIEW', 'CANCELLED'],
  ON_REVIEW: ['COMPLETED', 'IN_PROGRESS'],
  COMPLETED: [],
  CANCELLED: [],
};

export function statusClass(status: OrderStatus): string {
  return status.toLowerCase().replace(/_/g, '-');
}

export function formatBudget(value: string): string {
  const number = Number(value);
  if (!Number.isFinite(number)) return value;
  return number.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' ₽';
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' });
}

export function formatDeadline(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString('ru-RU');
}

export function normalizeTags(input: string | string[]): string[] {
  const raw = Array.isArray(input) ? input : input.split(/[,\n;]+/);
  const result: string[] = [];
  for (const item of raw) {
    const tag = item.trim().toLowerCase().slice(0, 50);
    if (tag && !result.includes(tag)) result.push(tag);
    if (result.length === 20) break;
  }
  return result;
}
