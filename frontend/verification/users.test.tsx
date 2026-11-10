import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginPage } from '../src/features/auth/LoginPage';
import { ChangePasswordPage } from '../src/features/auth/ChangePasswordPage';
import { UsersPage } from '../src/features/users/UsersPage';
import { generateTemporaryPassword } from '../src/features/users/temporaryPassword';
import type { Account, ApiFetch } from '../src/features/auth/contracts';

const admin: Account = { id: 'admin-id', name: 'Admin', login: 'admin@example.org', roles: ['ADMIN'], active: true, mustChangePassword: false, credentialsVersion: 0 };
const member: Account = { ...admin, id: 'user-id', name: 'Юра', login: 'yura@example.org', roles: ['VIEWER'] };
const page = { content: [member], page: 0, size: 20, totalElements: 21, totalPages: 2 };
describe('accounts pages', () => {
  it('generates random passwords satisfying the shared byte/class policy', () => {
    const values = Array.from({ length: 200 }, generateTemporaryPassword);
    for (const password of values) {
      expect(new TextEncoder().encode(password)).toHaveLength(20);
      expect(password).toMatch(/[A-Z]/); expect(password).toMatch(/[a-z]/);
      expect(password).toMatch(/[0-9]/); expect(password).toMatch(/[^a-zA-Z0-9]/);
    }
    expect(new Set(values).size).toBe(values.length);
  });
  it('routes temporary users to password change via the shared session', async () => {
    const user = userEvent.setup();
    const session = { login: vi.fn().mockResolvedValue({ user: { ...member, mustChangePassword: true } }), clear: vi.fn() };
    const navigate = vi.fn();
    render(<LoginPage session={session} navigate={navigate} />);
    await user.type(screen.getByLabelText('Email'), 'YURA@example.org');
    await user.type(screen.getByLabelText('Пароль'), 'Temporary-12345!');
    await user.click(screen.getByRole('button', { name: 'Войти' }));
    expect(session.login).toHaveBeenCalledWith({ login: 'yura@example.org', password: 'Temporary-12345!', clientType: 'WEB' });
    expect(navigate).toHaveBeenCalledWith('/change-password');
  });
  it('shows rate limiting without exposing account details and preserves input', async () => {
    const user = userEvent.setup();
    render(<LoginPage session={{ login: vi.fn().mockRejectedValue({ status: 429, message: 'secret account detail' }), clear: vi.fn() }} navigate={vi.fn()} />);
    await user.type(screen.getByLabelText('Email'), 'yura@example.org'); await user.type(screen.getByLabelText('Пароль'), 'Temporary-12345!');
    await user.click(screen.getByRole('button', { name: 'Войти' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Слишком много попыток');
    expect(screen.queryByText('secret account detail')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveValue('yura@example.org');
  });
  it('checks confirmation, changes password and clears the session', async () => {
    const user = userEvent.setup(); const fetch = vi.fn().mockResolvedValue(undefined); const clear = vi.fn();
    render(<ChangePasswordPage apiFetch={fetch as ApiFetch} session={{ clear }} navigate={vi.fn()} required />);
    await user.type(screen.getByLabelText('Текущий пароль'), 'Temporary-12345!');
    await user.type(screen.getByLabelText('Новый пароль'), 'Next-Password-123!');
    await user.type(screen.getByLabelText('Подтверждение пароля'), 'different');
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }));
    expect(fetch).not.toHaveBeenCalled();
    await user.clear(screen.getByLabelText('Подтверждение пароля'));
    await user.type(screen.getByLabelText('Подтверждение пароля'), 'Next-Password-123!');
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }));
    expect(clear).toHaveBeenCalledOnce(); expect(await screen.findByRole('status')).toHaveTextContent('Войдите с новым паролем');
    expect(fetch.mock.calls[0][0]).toBe('/api/auth/change-password');
  });
  it('does not fetch admin data for VIEWER or an account awaiting password change', () => {
    const fetch = vi.fn();
    const view = render(<UsersPage apiFetch={fetch as ApiFetch} currentUser={member} />);
    expect(screen.getByRole('alert')).toHaveTextContent('доступно администратору');
    view.rerender(<UsersPage apiFetch={fetch as ApiFetch} currentUser={{ ...admin, mustChangePassword: true }} />);
    expect(fetch).not.toHaveBeenCalled();
  });
  it('paginates on server and resets with confirmation, clearing the temporary password', async () => {
    const user = userEvent.setup(); const fetch = vi.fn().mockResolvedValue(page);
    render(<UsersPage apiFetch={fetch as ApiFetch} currentUser={admin} />);
    await screen.findByText(member.login);
    await user.click(screen.getByRole('button', { name: 'Далее' }));
    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/admin/users?page=1&size=20', expect.anything()));
    await user.click(await screen.findByRole('button', { name: `Сбросить пароль ${member.login}` }));
    const input = screen.getByLabelText('Временный пароль') as HTMLInputElement; const password = input.value;
    await user.click(screen.getByLabelText(/Я сохранил пароль/));
    await user.click(screen.getByRole('button', { name: 'Подтвердить сброс' }));
    await waitFor(() => expect(screen.queryByLabelText('Временный пароль')).not.toBeInTheDocument());
    expect(fetch).toHaveBeenCalledWith('/api/admin/users/user-id/reset-password', expect.objectContaining({ body: JSON.stringify({ temporaryPassword: password }) }));
    expect(localStorage.length).toBe(0);
  });
  it('keeps account form and temporary password after a failed save; cancel clears them', async () => {
    const user = userEvent.setup();
    const fetch = vi.fn().mockImplementation((_path: string, init?: RequestInit) => init?.method ? Promise.reject({ message: 'Email уже используется', status: 409 }) : Promise.resolve(page));
    render(<UsersPage apiFetch={fetch as ApiFetch} currentUser={admin} />);
    await screen.findByText(member.login); await user.click(screen.getByRole('button', { name: 'Создать аккаунт' }));
    await user.type(screen.getByLabelText('Имя'), 'Новый'); await user.type(screen.getByLabelText('Email для входа'), 'new@example.org');
    const password = (screen.getByLabelText('Временный пароль') as HTMLInputElement).value;
    await user.click(screen.getByLabelText(/Я сохранил пароль/)); await user.click(screen.getByRole('button', { name: 'Сохранить' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Email уже используется');
    expect(screen.getByLabelText('Имя')).toHaveValue('Новый'); expect(screen.getByLabelText('Временный пароль')).toHaveValue(password);
    await user.click(screen.getByRole('button', { name: 'Отмена' }));
    expect(screen.queryByLabelText('Временный пароль')).not.toBeInTheDocument();
  });
});
