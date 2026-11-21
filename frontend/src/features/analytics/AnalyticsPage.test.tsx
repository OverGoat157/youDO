import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AnalyticsPage } from './AnalyticsPage';
import type { AnalyticsSummary } from './api';

const summary: AnalyticsSummary = {
  total: 5, active: 4, archived: 1, urgent: 2, averageBudget: 250,
  byCategory: [{ key: 'DEVELOPMENT', name: 'Разработка', count: 3 }, { key: 'DESIGN', name: 'Дизайн', count: 1 }],
  byStatus: [
    { key: 'OPEN', name: 'OPEN', count: 2 }, { key: 'IN_PROGRESS', name: 'IN_PROGRESS', count: 1 },
    { key: 'ON_REVIEW', name: 'ON_REVIEW', count: 0 }, { key: 'COMPLETED', name: 'COMPLETED', count: 1 },
    { key: 'CANCELLED', name: 'CANCELLED', count: 0 },
  ],
  byPriority: [{ key: 'LOW', name: 'LOW', count: 2 }, { key: 'MEDIUM', name: 'MEDIUM', count: 1 }, { key: 'HIGH', name: 'HIGH', count: 1 }],
  priorityDate: '2026-11-22',
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

describe('AnalyticsPage', () => {
  it('показывает счётчики, средний бюджет и группировки с понятными подписями', async () => {
    fetchMock.mockResolvedValue(jsonResponse(summary));
    render(<AnalyticsPage />);

    expect(await screen.findByText('Всего заказов')).toBeInTheDocument();
    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/analytics/summary');
    expect(screen.getByText('Срочные среди активных').nextSibling).toHaveTextContent('2');
    expect(screen.getByText('Средний бюджет активных').nextSibling).toHaveTextContent(/250,00\s₽/);

    const categories = within(screen.getByRole('region', { name: 'По категориям' }));
    expect(categories.getByRole('row', { name: /Разработка/ })).toHaveTextContent('3');
    const statuses = within(screen.getByRole('region', { name: 'По статусам' }));
    expect(statuses.getByRole('row', { name: /На проверке/ })).toHaveTextContent('0');
    const priorities = within(screen.getByRole('region', { name: 'По приоритету' }));
    expect(priorities.getByRole('row', { name: /Высокий/ })).toHaveTextContent('1');
  });

  it('пустая база: прочерк вместо среднего и сообщение в группе категорий', async () => {
    fetchMock.mockResolvedValue(jsonResponse({
      ...summary, total: 0, active: 0, archived: 0, urgent: 0, averageBudget: null, byCategory: [],
    }));
    render(<AnalyticsPage />);

    expect(await screen.findByText('Средний бюджет активных')).toBeInTheDocument();
    expect(screen.getByText('Средний бюджет активных').nextSibling).toHaveTextContent('—');
    expect(within(screen.getByRole('region', { name: 'По категориям' })).getByText('Активных заказов нет.')).toBeInTheDocument();
  });

  it('показывает ошибку сервера и перезапрашивает по кнопке', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({
      status: 500, code: 'INTERNAL_ERROR', message: 'Внутренняя ошибка сервера',
      fieldErrors: [], correlationId: 'c-1', timestamp: '2026-11-22T10:00:00Z',
    }, 500)).mockResolvedValueOnce(jsonResponse(summary));
    const user = userEvent.setup();
    render(<AnalyticsPage />);

    expect(await screen.findByText('Внутренняя ошибка сервера')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Повторить загрузку' }));
    expect(await screen.findByText('Всего заказов')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
