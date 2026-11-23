import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginPage } from './LoginPage';
import { ChangePasswordPage } from './ChangePasswordPage';
import { inspectPassword } from './passwordPolicy';

describe('password entry', () => {
  it('shows and hides the login password without submitting or changing it', async () => {
    const user = userEvent.setup();
    const login = vi.fn();
    render(<LoginPage session={{ login, clear: vi.fn() }} navigate={vi.fn()} />);
    const password = screen.getByLabelText('Пароль');
    await user.type(password, 'Only-Test-42!');
    expect(password).toHaveAttribute('type', 'password');
    await user.click(screen.getByRole('button', { name: 'Показать пароль: пароль' }));
    expect(password).toHaveAttribute('type', 'text');
    expect(password).toHaveValue('Only-Test-42!');
    await user.click(screen.getByRole('button', { name: 'Скрыть пароль: пароль' }));
    expect(password).toHaveAttribute('type', 'password');
    expect(password).toHaveValue('Only-Test-42!');
    expect(login).not.toHaveBeenCalled();
  });

  it('explains an 11-character password and validates it before calling the API', async () => {
    const user = userEvent.setup();
    const apiFetch = vi.fn().mockResolvedValue(undefined);
    render(<ChangePasswordPage apiFetch={apiFetch} session={{ clear: vi.fn() }} navigate={vi.fn()} required />);
    await user.type(screen.getByLabelText('Текущий пароль'), 'Temporary-Test-42!');
    await user.type(screen.getByLabelText('Новый пароль'), 'ShortWord7!');
    await user.type(screen.getByLabelText('Подтверждение пароля'), 'ShortWord7!');
    expect(screen.getByRole('status')).toHaveTextContent('Сейчас: 11 символов. Нужно ещё минимум 1 символ.');
    expect(screen.getByText('MangoRiver42!')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Пароль слишком короткий: 11 символов. Нужно минимум 12.');
    expect(apiFetch).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText('Новый пароль'), 'x');
    await user.type(screen.getByLabelText('Подтверждение пароля'), 'x');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }));
    expect(apiFetch).toHaveBeenCalledWith('/api/auth/change-password', expect.objectContaining({
      body: JSON.stringify({ currentPassword: 'Temporary-Test-42!', newPassword: 'ShortWord7!x' }),
    }));
  });

  it('toggles each change-password field independently without submitting the form', async () => {
    const user = userEvent.setup();
    const apiFetch = vi.fn();
    render(<ChangePasswordPage apiFetch={apiFetch} session={{ clear: vi.fn() }} navigate={vi.fn()} />);
    const current = screen.getByLabelText('Текущий пароль');
    const next = screen.getByLabelText('Новый пароль');
    const confirmation = screen.getByLabelText('Подтверждение пароля');
    await user.click(screen.getByRole('button', { name: 'Показать пароль: текущий пароль' }));
    expect(current).toHaveAttribute('type', 'text');
    expect(next).toHaveAttribute('type', 'password');
    expect(confirmation).toHaveAttribute('type', 'password');
    await user.click(screen.getByRole('button', { name: 'Показать пароль: новый пароль' }));
    await user.click(screen.getByRole('button', { name: 'Показать пароль: подтверждение пароля' }));
    expect(next).toHaveAttribute('type', 'text');
    expect(confirmation).toHaveAttribute('type', 'text');
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it('counts UTF-8 bytes and matches the server boundaries for Cyrillic and emoji', () => {
    expect(inspectPassword('Аа7!😀aa').problems).toEqual([]);
    expect(inspectPassword('Aa1!' + '😀'.repeat(17)).bytes).toBe(72);
    expect(inspectPassword('Aa1!' + '😀'.repeat(17)).problems).toEqual([]);
    expect(inspectPassword('Aa1!' + '😀'.repeat(18)).problems).toContain(
      'Пароль слишком длинный: 76 байт UTF-8. Допускается не больше 72.');
    expect(inspectPassword('Abcdefghij7 ').problems).toContain('Добавьте спецсимвол, например !, @, # или ?.');
    expect(inspectPassword('Aa1!abcdefgh\ud800').problems).toContain('Удалите недопустимый символ из пароля.');
  });
});
