import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from './App';
import { session, type Account } from '../api/session';
import { setAccessToken } from '../api/client';

const admin: Account = { id: 'admin', name: 'Юра', login: 'admin@example.org', roles: ['ADMIN'], active: true, mustChangePassword: false, credentialsVersion: 0 };
const member: Account = { ...admin, id: 'member', name: 'Редактор', login: 'member@example.org', roles: ['EDITOR'] };
const fetchMock = vi.fn();
const response = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });

beforeEach(() => {
  session.markRestored();
  session.clear();
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  window.history.replaceState(null, '', '/admin/users');
});
afterEach(() => { vi.unstubAllGlobals(); session.clear(); });

describe('user pages in the shared application', () => {
  it('redirects an anonymous visitor from the admin URL to login without requesting users', async () => {
    render(<App />);
    expect(await screen.findByRole('heading', { name: 'Вход в youDO' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('guards temporary accounts and keeps the success notice after clearing the session', async () => {
    session.setUser({ ...admin, mustChangePassword: true });
    setAccessToken('temporary-access');
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole('heading', { name: 'Смена пароля' });
    expect(fetchMock).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText('Текущий пароль'), 'Temporary-12345!');
    await user.type(screen.getByLabelText('Новый пароль'), 'Next-Password-123!');
    await user.type(screen.getByLabelText('Подтверждение пароля'), 'Next-Password-123!');
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }));
    await screen.findByRole('heading', { name: 'Вход в youDO' });
    expect(screen.getByRole('status')).toHaveTextContent('Пароль изменён. Войдите с новым паролем.');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/change-password');
    expect(session.getSnapshot().status).toBe('anonymous');
  });

  it('creates and edits accounts through the shared authenticated HTTP client', async () => {
    session.setUser(admin);
    setAccessToken('admin-access');
    fetchMock.mockImplementation(async (_url: string, init: RequestInit) => init.method === 'POST' || init.method === 'PUT'
      ? response(member)
      : response({ content: [member], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
    const user = userEvent.setup();
    render(<App />);
    await screen.findByText(member.login);
    await user.click(screen.getByRole('button', { name: 'Создать аккаунт' }));
    await user.type(screen.getByLabelText('Имя'), 'Новый');
    await user.type(screen.getByLabelText('Email для входа'), 'new@example.org');
    const password = (screen.getByLabelText('Временный пароль') as HTMLInputElement).value;
    await user.click(screen.getByLabelText(/Я сохранил пароль/));
    await user.click(screen.getByRole('button', { name: 'Сохранить' }));
    await waitFor(() => expect(screen.queryByLabelText('Временный пароль')).not.toBeInTheDocument());
    const create = fetchMock.mock.calls.find(([, init]) => init.method === 'POST')!;
    expect(create[0]).toBe('/api/admin/users');
    expect(JSON.parse(create[1].body)).toEqual({ name: 'Новый', login: 'new@example.org', roles: ['VIEWER'], temporaryPassword: password });
    expect(new Headers(create[1].headers).get('Authorization')).toBe('Bearer admin-access');
    await user.click(await screen.findByRole('button', { name: `Изменить ${member.login}` }));
    await user.clear(screen.getByLabelText('Имя'));
    await user.type(screen.getByLabelText('Имя'), 'Обновлённый');
    await user.click(screen.getByLabelText('Аккаунт активен'));
    await user.click(screen.getByRole('button', { name: 'Сохранить' }));
    await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init.method === 'PUT')).toBe(true));
    const edit = fetchMock.mock.calls.find(([, init]) => init.method === 'PUT')!;
    expect(edit[0]).toBe('/api/admin/users/member');
    expect(JSON.parse(edit[1].body)).toEqual({ name: 'Обновлённый', login: member.login, roles: ['EDITOR'], active: false });
  });

  it('does not expose the users screen to VIEWER', async () => {
    session.setUser({ ...member, roles: ['VIEWER'] });
    fetchMock.mockImplementation(async (url: string) => response(url.startsWith('/api/categories') ? []
      : { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
    render(<App />);
    await waitFor(() => expect(window.location.pathname).toBe('/orders'));
    expect(screen.queryByRole('button', { name: 'Создать аккаунт' })).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/admin/users'))).toBe(false);
  });
});
