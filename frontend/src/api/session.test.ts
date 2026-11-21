import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApiRequestError } from './client';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const fetchMock = vi.fn();

beforeEach(() => {
  vi.resetModules();
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  window.history.replaceState(null, '', '/orders');
});

const me = {
  id: 'u1', name: 'Ангелина', login: 'angelina@example.com', roles: ['EDITOR'], mustChangePassword: false,
};

describe('session', () => {
  it('после входа держит access в памяти и открывает /orders', async () => {
    const client = await import('./client');
    const { session } = await import('./session');
    fetchMock.mockResolvedValueOnce(jsonResponse({ accessToken: 'access-1', tokenType: 'Bearer', expiresIn: 900, user: me }));

    const { user } = await session.login({ login: ' Angelina@Example.com ', password: 'secret', clientType: 'WEB' });

    expect(user.name).toBe('Ангелина');
    expect(user.roles).toEqual(['EDITOR']);
    expect(user.active).toBe(true);
    expect(client.getAccessToken()).toBe('access-1');
    expect(session.getSnapshot().status).toBe('authenticated');
    const requested = fetchMock.mock.calls[0][0] as string;
    expect(requested).toBe('/api/auth/login');
    // Access не попадает в читаемые cookie и остаётся только в памяти модуля.
    expect(document.cookie).not.toContain('access-1');
  });

  it('временный пароль переводит сессию в режим смены пароля', async () => {
    const { session } = await import('./session');
    fetchMock.mockResolvedValueOnce(jsonResponse({
      accessToken: 'access-2', user: { ...me, mustChangePassword: true },
    }));

    await session.login({ login: me.login, password: 'temp', clientType: 'WEB' });

    expect(session.getSnapshot().status).toBe('password-change-required');
    expect(session.getSnapshot().user?.mustChangePassword).toBe(true);
  });

  it('восстанавливает сессию через refresh и /me', async () => {
    const { session } = await import('./session');
    fetchMock.mockImplementation(async (url: string) => {
      if (url === '/api/auth/refresh') return jsonResponse({ accessToken: 'access-3', user: me });
      if (url === '/api/auth/me') return jsonResponse(me);
      return jsonResponse({ status: 404, message: 'нет' }, 404);
    });

    await session.restore();

    expect(session.getSnapshot().status).toBe('authenticated');
    expect(session.getSnapshot().user?.login).toBe(me.login);
  });

  it('без cookie refresh возвращает анонимное состояние без ошибки', async () => {
    const { session } = await import('./session');
    fetchMock.mockResolvedValueOnce(jsonResponse({ status: 401, message: 'Нет сессии' }, 401));

    await session.restore();

    expect(session.getSnapshot().status).toBe('anonymous');
    expect(session.getSnapshot().notice).toBeNull();
  });

  it('истёкший refresh завершает сессию и объясняет причину', async () => {
    const client = await import('./client');
    const { session } = await import('./session');
    fetchMock.mockImplementation(async (url: string) => {
      if (url === '/api/auth/login') return jsonResponse({ accessToken: 'access-4', user: me });
      if (url === '/api/auth/refresh') return jsonResponse({ status: 401, message: 'Нет сессии' }, 401);
      return jsonResponse({ status: 401, message: 'Истёк access' }, 401);
    });

    await session.login({ login: me.login, password: 'secret', clientType: 'WEB' });
    const error = (await client.apiFetch('/api/orders').catch(cause => cause)) as ApiRequestError;

    expect(error.status).toBe(401);
    expect(client.getAccessToken()).toBeNull();
    expect(session.getSnapshot().status).toBe('anonymous');
    expect(session.getSnapshot().notice).toContain('Сессия истекла');
  });

  it('выход очищает состояние даже при ошибке запроса', async () => {
    const client = await import('./client');
    const { session } = await import('./session');
    fetchMock.mockRejectedValueOnce(new TypeError('offline'));

    await session.logout();

    expect(client.getAccessToken()).toBeNull();
    expect(session.getSnapshot().status).toBe('anonymous');
  });
});
