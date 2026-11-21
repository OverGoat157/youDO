import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ApiRequestError, apiFetch, setAccessToken, setRefreshHandler,
  setSessionExpiredHandler,
} from './client';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  setAccessToken(null);
  setRefreshHandler(null);
  setSessionExpiredHandler(() => {});
  vi.stubGlobal('fetch', fetchMock);
});

describe('apiFetch', () => {
  it('передаёт access в заголовке и добавляет correlation id', async () => {
    setAccessToken('token-1');
    fetchMock.mockResolvedValueOnce(jsonResponse({ ok: true }));

    await expect(apiFetch<{ ok: boolean }>('/api/orders')).resolves.toEqual({ ok: true });

    const [, init] = fetchMock.mock.calls[0];
    const headers = init.headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer token-1');
    expect(headers.get('X-Correlation-ID')).toBeTruthy();
    expect(init.credentials).toBe('include');
  });

  it('возвращает undefined для 204', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await expect(apiFetch<void>('/api/auth/logout', { method: 'POST' })).resolves.toBeUndefined();
  });

  it('выполняет один общий refresh на одновременные 401 и повторяет запрос один раз', async () => {
    let refreshCalls = 0;
    setRefreshHandler(async () => {
      refreshCalls += 1;
      setAccessToken('token-2');
      return 'token-2';
    });
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      const headers = init.headers as Headers;
      if (headers.get('Authorization') === 'Bearer token-2') return jsonResponse({ url });
      return jsonResponse({ status: 401, code: 'UNAUTHORIZED', message: 'Нет доступа' }, 401);
    });

    const results = await Promise.all([
      apiFetch<{ url: string }>('/api/orders?page=0'),
      apiFetch<{ url: string }>('/api/orders?page=1'),
    ]);

    expect(refreshCalls).toBe(1);
    expect(results.map(item => item.url)).toEqual(['/api/orders?page=0', '/api/orders?page=1']);
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('не запускает refresh на 403 и сохраняет структуру ошибки', async () => {
    let refreshCalls = 0;
    setRefreshHandler(async () => { refreshCalls += 1; return 'token'; });
    const errorBody = {
      status: 403,
      code: 'PASSWORD_CHANGE_REQUIRED',
      message: 'Сначала смените временный пароль',
      fieldErrors: [{ field: 'password', message: 'Требуется смена' }],
      correlationId: 'corr-1',
    };
    fetchMock.mockResolvedValueOnce(jsonResponse(errorBody, 403));

    const error = (await apiFetch('/api/orders').catch(cause => cause)) as ApiRequestError;

    expect(refreshCalls).toBe(0);
    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error.status).toBe(403);
    expect(error.code).toBe('PASSWORD_CHANGE_REQUIRED');
    expect(error.correlationId).toBe('corr-1');
    expect(error.fieldErrors).toEqual([{ field: 'password', message: 'Требуется смена' }]);
  });

  it('завершает сессию, если refresh не удался, и не повторяет запрос бесконечно', async () => {
    const expired = vi.fn();
    setSessionExpiredHandler(expired);
    setRefreshHandler(async () => null);
    fetchMock.mockResolvedValue(jsonResponse({ status: 401, message: 'Истёк access' }, 401));

    const error = (await apiFetch('/api/orders').catch(cause => cause)) as ApiRequestError;

    expect(error.status).toBe(401);
    expect(expired).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('сообщает о недоступном сервере отдельной ошибкой', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    const error = (await apiFetch('/api/orders').catch(cause => cause)) as ApiRequestError;
    expect(error.status).toBe(0);
    expect(error.message).toContain('Нет связи с сервером');
  });
});
