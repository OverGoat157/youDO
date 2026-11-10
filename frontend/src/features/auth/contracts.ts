/** Supplied by the shared API/session layer. Tokens are never stored by these pages. */
export type Role = 'VIEWER' | 'EDITOR' | 'ADMIN';
export interface Account {
  id: string; name: string; login: string; roles: Role[];
  active: boolean; mustChangePassword: boolean; credentialsVersion: number;
}
export interface ApiError extends Error {
  status?: number; code?: string; correlationId?: string;
  fieldErrors?: { field: string; message: string }[];
}
export type ApiFetch = <T>(path: string, init?: RequestInit) => Promise<T>;
export interface AuthSession {
  login(input: { login: string; password: string; clientType: 'WEB' }): Promise<{ user: Account }>;
  clear(): void;
}
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export function jsonRequest(method: string, value: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) };
}
