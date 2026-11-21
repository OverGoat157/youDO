import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuditPage } from './AuditPage';
import { buildAuditParams, EMPTY_AUDIT_FILTER, type AuditEvent } from './api';
import type { Page } from '../orders/types';

const login: AuditEvent = {
  id: 'e1', actorId: 'a1', actorLogin: 'admin@youdo.local', action: 'LOGIN', entityType: 'ACCOUNT',
  entityId: null, occurredAt: '2026-11-21T10:00:00Z', summary: 'Успешный вход', correlationId: 'corr-1',
};
const failed: AuditEvent = {
  ...login, id: 'e2', actorId: null, actorLogin: null, action: 'LOGIN_FAILED', summary: 'Вход отклонён', correlationId: 'corr-2',
};

function page(content: AuditEvent[], totalElements = content.length, totalPages = 1): Page<AuditEvent> {
  return { content, page: 0, size: 20, totalElements, totalPages };
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const fetchMock = vi.fn();

function lastParams(): URLSearchParams {
  const url = String(fetchMock.mock.calls.at(-1)![0]);
  return new URL(url, 'http://localhost').searchParams;
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  fetchMock.mockImplementation(async () => jsonResponse(page([login, failed], 45, 3)));
});

describe('AuditPage', () => {
  it('показывает записи журнала, новые первыми, с понятными названиями', async () => {
    render(<AuditPage />);

    expect(await screen.findByText('Успешный вход')).toBeInTheDocument();
    const table = within(screen.getByRole('table'));
    expect(table.getByText('Неудачный вход')).toBeInTheDocument();
    expect(table.getByText('не определён')).toBeInTheDocument();
    expect(table.getByText('corr-2')).toBeInTheDocument();
    expect(screen.getByText(/Страница 1 из 3 · Записей 45/)).toBeInTheDocument();

    const params = lastParams();
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/^\/api\/admin\/audit\?/);
    expect(params.get('sort')).toBe('occurredAt,desc');
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('20');
  });

  it('передаёт фильтры на сервер и начинает с первой страницы', async () => {
    const user = userEvent.setup();
    render(<AuditPage />);
    await screen.findByText('Успешный вход');

    await user.click(screen.getByRole('button', { name: 'Далее' }));
    await waitFor(() => expect(lastParams().get('page')).toBe('1'));

    await user.type(screen.getByLabelText('Поиск'), 'admin');
    await user.type(screen.getByLabelText('Действие'), 'LOGIN_FAILED');
    await user.selectOptions(screen.getByLabelText('Объект'), 'ACCOUNT');
    await user.click(screen.getByRole('button', { name: 'Найти' }));

    await waitFor(() => expect(lastParams().get('q')).toBe('admin'));
    const params = lastParams();
    expect(params.get('action')).toBe('LOGIN_FAILED');
    expect(params.get('entityType')).toBe('ACCOUNT');
    expect(params.get('page')).toBe('0');
  });

  it('меняет сортировку', async () => {
    const user = userEvent.setup();
    render(<AuditPage />);
    await screen.findByText('Успешный вход');

    await user.selectOptions(screen.getByLabelText('Сортировка'), 'actorLogin,asc');

    await waitFor(() => expect(lastParams().get('sort')).toBe('actorLogin,asc'));
  });

  it('сообщает о пустом результате', async () => {
    fetchMock.mockImplementation(async () => jsonResponse(page([])));
    render(<AuditPage />);

    expect(await screen.findByText('Записей по этим условиям нет.')).toBeInTheDocument();
  });

  it('показывает ошибку сервера', async () => {
    fetchMock.mockImplementation(async () => jsonResponse({
      status: 400, code: 'VALIDATION_ERROR', message: 'page ≥ 0, size от 1 до 100',
      fieldErrors: [], correlationId: 'c', timestamp: '2026-11-21T10:00:00Z',
    }, 400));
    render(<AuditPage />);

    expect(await screen.findByText(/size от 1 до 100/)).toBeInTheDocument();
  });

  it('не отправляет запрос, если начало периода позже конца', async () => {
    const user = userEvent.setup();
    render(<AuditPage />);
    await screen.findByText('Успешный вход');
    const calls = fetchMock.mock.calls.length;

    await user.type(screen.getByLabelText('С'), '2026-11-21T12:00');
    await user.type(screen.getByLabelText('По'), '2026-11-21T10:00');
    await user.click(screen.getByRole('button', { name: 'Найти' }));

    expect(await screen.findByText('Начало периода должно быть раньше конца.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.length).toBe(calls);
  });
});

describe('buildAuditParams', () => {
  it('переводит местное время в ISO UTC и пропускает пустые условия', () => {
    const params = buildAuditParams({
      ...EMPTY_AUDIT_FILTER, q: '  ', from: '2026-11-21T10:00', page: 2, size: 50, sort: 'action,asc',
    });
    expect(params.has('q')).toBe(false);
    expect(params.get('from')).toBe(new Date('2026-11-21T10:00').toISOString());
    expect(params.has('to')).toBe(false);
    expect(params.get('page')).toBe('2');
    expect(params.get('size')).toBe('50');
  });
});
