export interface FieldError {
  field: string;
  message: string;
}

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly correlationId?: string;
  readonly fieldErrors: FieldError[];

  constructor(status: number, message: string, options: {
    code?: string;
    correlationId?: string;
    fieldErrors?: FieldError[];
  } = {}) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = options.code;
    this.correlationId = options.correlationId;
    this.fieldErrors = options.fieldErrors ?? [];
  }
}

export type ApiFetch = <T>(path: string, init?: RequestInit) => Promise<T>;
export type JsonHeaders = Record<string, string>;

let accessToken: string | null = null;
let refreshHandler: (() => Promise<string | null>) | null = null;
let expiredHandler: () => void = () => {};
let passwordChangeHandler: () => void = () => {};
let refreshInFlight: Promise<string | null> | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function setRefreshHandler(handler: (() => Promise<string | null>) | null): void {
  refreshHandler = handler;
}

export function setSessionExpiredHandler(handler: () => void): void {
  expiredHandler = handler;
}

export function setPasswordChangeRequiredHandler(handler: () => void): void {
  passwordChangeHandler = handler;
}

function isAuthEndpoint(path: string): boolean {
  return path.startsWith('/api/auth/login') || path.startsWith('/api/auth/refresh');
}

function buildHeaders(init?: RequestInit): Headers {
  const headers = new Headers(init?.headers);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  if (init?.body !== undefined && init?.body !== null && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (accessToken && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${accessToken}`);
  if (!headers.has('X-Correlation-ID') && typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    headers.set('X-Correlation-ID', crypto.randomUUID());
  }
  return headers;
}

function canReplay(method: string, body: BodyInit | null | undefined): boolean {
  const verb = method.toUpperCase();
  if (verb === 'GET' || verb === 'HEAD') return true;
  return body === undefined || body === null || typeof body === 'string';
}

async function send(path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(path, {
    ...init,
    headers: buildHeaders(init),
    credentials: 'include',
    cache: 'no-store',
  });
}

function normalizeError(payload: unknown, status: number, fallback: string): ApiRequestError {
  if (payload && typeof payload === 'object') {
    const body = payload as Record<string, unknown>;
    const fieldErrors = Array.isArray(body.fieldErrors)
      ? (body.fieldErrors as Array<Record<string, unknown>>)
          .filter(item => item && typeof item.field === 'string')
          .map(item => ({ field: String(item.field), message: String(item.message ?? '') }))
      : [];
    return new ApiRequestError(
      typeof body.status === 'number' ? body.status : status,
      typeof body.message === 'string' && body.message ? body.message : fallback,
      {
        code: typeof body.code === 'string' ? body.code : undefined,
        correlationId: typeof body.correlationId === 'string' ? body.correlationId : undefined,
        fieldErrors,
      },
    );
  }
  return new ApiRequestError(status, fallback);
}

async function readError(response: Response): Promise<ApiRequestError> {
  const fallback = response.status === 401
    ? 'Требуется вход. Сессия недействительна.'
    : response.status === 403
      ? 'Недостаточно прав для этого действия.'
      : response.status === 404
        ? 'Запись не найдена.'
        : response.status === 409
          ? 'Данные уже изменены другим пользователем.'
          : response.status === 429
            ? 'Слишком много запросов. Повторите позже.'
            : response.status >= 500
              ? 'Сервис недоступен. Повторите позже.'
              : `Запрос отклонён (${response.status}).`;
  let payload: unknown = null;
  const text = await response.text();
  if (text) {
    try {
      payload = JSON.parse(text) as unknown;
    } catch {
      payload = null;
    }
  }
  return normalizeError(payload, response.status, fallback);
}

function refreshAccess(): Promise<string | null> {
  if (!refreshHandler) return Promise.resolve(null);
  if (!refreshInFlight) {
    refreshInFlight = refreshHandler()
      .catch(() => null)
      .finally(() => { refreshInFlight = null; });
  }
  return refreshInFlight;
}

async function notifyFailure(error: ApiRequestError, path: string): Promise<void> {
  if (error.status === 401 && !isAuthEndpoint(path)) expiredHandler();
  if (error.status === 403 && error.code === 'PASSWORD_CHANGE_REQUIRED') passwordChangeHandler();
}

async function parseBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const text = await response.text();
  if (!text) return undefined;
  const type = response.headers.get('Content-Type') ?? '';
  if (type.includes('json')) return JSON.parse(text) as unknown;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = init.method ?? 'GET';
  let response: Response;
  try {
    response = await send(path, init);
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    throw new ApiRequestError(0, 'Нет связи с сервером. Проверьте сеть и повторите попытку.');
  }

  if (response.status === 401 && !isAuthEndpoint(path) && canReplay(method, init.body)) {
    const token = await refreshAccess();
    if (token) {
      try {
        response = await send(path, init);
      } catch (cause) {
        if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
        throw new ApiRequestError(0, 'Нет связи с сервером. Проверьте сеть и повторите попытку.');
      }
    }
  }

  if (!response.ok) {
    const error = await readError(response);
    await notifyFailure(error, path);
    throw error;
  }
  return (await parseBody(response)) as T;
}

export async function apiDownload(path: string): Promise<{ blob: Blob; filename: string | null }> {
  const sendOnce = () => send(path, { method: 'GET', headers: { Accept: 'text/csv, application/json' } });
  let response: Response;
  try {
    response = await sendOnce();
  } catch {
    throw new ApiRequestError(0, 'Нет связи с сервером. Проверьте сеть и повторите попытку.');
  }
  if (response.status === 401) {
    const token = await refreshAccess();
    if (token) response = await sendOnce();
  }
  if (!response.ok) {
    const error = await readError(response);
    await notifyFailure(error, path);
    throw error;
  }
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  return { blob: await response.blob(), filename: match ? decodeURIComponent(match[1]) : null };
}

export function jsonRequest(method: string, value: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) };
}
