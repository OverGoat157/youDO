import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OrdersPage } from './OrdersPage';
import type { Account } from '../../api/session';
import type { OrderResponse, Page } from './types';

const editor: Account = {
  id: 'u1', name: 'Ангелина', login: 'angelina@example.com', roles: ['EDITOR'],
  mustChangePassword: false, active: true, credentialsVersion: 1,
};

const viewer: Account = { ...editor, id: 'u2', login: 'viewer@example.com', roles: ['VIEWER'] };

const order: OrderResponse = {
  id: '8f1c4c6e-0000-4000-8000-000000000001',
  code: 'WEB-14',
  title: 'Собрать лендинг',
  description: 'Описание',
  categoryId: 'c1',
  categoryName: 'Веб-разработка',
  status: 'IN_PROGRESS',
  budget: '120000.00',
  deadline: '2026-12-01',
  customerId: 1,
  customerName: 'Иван Петров',
  freelancerId: 2,
  freelancerName: 'Мария Иванова',
  urgent: true,
  tags: ['backend'],
  priority: 'HIGH',
  archived: false,
  createdAt: '2026-11-20T10:00:00Z',
  updatedAt: '2026-11-20T10:00:00Z',
  version: 3,
};

const page: Page<OrderResponse> = { content: [order], page: 0, size: 20, totalElements: 1, totalPages: 1 };

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  fetchMock.mockImplementation(async (url: string) => {
    if (url.startsWith('/api/categories')) {
      return jsonResponse([{ id: 'c1', code: 'WEB', name: 'Веб-разработка', active: true }]);
    }
    if (url.startsWith('/api/orders')) return jsonResponse(page);
    return jsonResponse({ status: 404, message: 'не найдено' }, 404);
  });
});

describe('OrdersPage', () => {
  it('показывает заказы из API с серверной сортировкой и страницей', async () => {
    render(<OrdersPage user={editor} />);

    expect(await screen.findByText('Собрать лендинг')).toBeInTheDocument();
    const table = screen.getByRole('region', { name: 'Таблица заказов' });
    expect(within(table).getByText('WEB-14')).toBeInTheDocument();
    expect(within(table).getByText('В работе')).toBeInTheDocument();
    expect(within(table).getByText('120 000,00 ₽')).toBeInTheDocument();
    expect(screen.getByText('Страница 1 из 1 · Найдено 1')).toBeInTheDocument();

    const ordersUrl = fetchMock.mock.calls
      .map(call => call[0] as string)
      .find(url => url.startsWith('/api/orders'));
    expect(ordersUrl).toContain('sort=createdAt%2Cdesc');
    expect(ordersUrl).toContain('archived=false');
    expect(ordersUrl).toContain('page=0');
    expect(ordersUrl).toContain('size=20');
  });

  it('передаёт поиск и фильтры серверу и сбрасывает страницу', async () => {
    const user = userEvent.setup();
    render(<OrdersPage user={editor} />);
    await screen.findByText('Собрать лендинг');

    await user.type(screen.getByLabelText('Поиск'), 'лендинг');
    await user.selectOptions(screen.getByLabelText('Статус'), 'IN_PROGRESS');
    await user.selectOptions(screen.getByLabelText('Бюджет'), 'gte');
    await user.type(screen.getByLabelText('Значение бюджета'), '1000');
    await user.click(screen.getByRole('button', { name: 'Найти' }));

    await waitFor(() => {
      const last = fetchMock.mock.calls.map(call => call[0] as string).filter(url => url.startsWith('/api/orders')).pop()!;
      expect(last).toContain('q=%D0%BB%D0%B5%D0%BD%D0%B4%D0%B8%D0%BD%D0%B3');
      expect(last).toContain('status=IN_PROGRESS');
      expect(last).toContain('budgetOp=gte');
      expect(last).toContain('budgetValue=1000');
      expect(last).toContain('page=0');
    });
  });

  it('не показывает редактирование зрителю', async () => {
    render(<OrdersPage user={viewer} />);
    await screen.findByText('Собрать лендинг');

    expect(screen.queryByRole('link', { name: 'Создать заказ' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Выбрать все заказы на странице')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Экспорт CSV' })).toBeInTheDocument();
  });

  it('показывает пустое состояние при отсутствии результатов', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      if (url.startsWith('/api/categories')) return jsonResponse([]);
      return jsonResponse({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    });
    render(<OrdersPage user={editor} />);

    expect(await screen.findByText(/Заказов пока нет/)).toBeInTheDocument();
  });

  it('открывает массовые действия у выбранного заказа', async () => {
    const user = userEvent.setup();
    render(<OrdersPage user={editor} />);
    await screen.findByText('Собрать лендинг');

    await user.click(screen.getByLabelText('Выбрать заказ WEB-14'));

    const panel = screen.getByRole('form', { name: 'Массовые действия' });
    expect(within(panel).getByText('Выбрано заказов: 1')).toBeInTheDocument();
    expect(within(panel).getByLabelText('Действие')).toBeInTheDocument();
  });
});
