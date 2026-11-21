import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CsvPage } from './CsvPage';
import type { ImportPreview } from './api';
import type { Account } from '../../api/session';

const editor: Account = {
  id: 'u1', name: 'Сергей', login: 'sergey@example.com', roles: ['EDITOR'],
  mustChangePassword: false, active: true, credentialsVersion: 1,
};
const viewer: Account = { ...editor, id: 'u2', roles: ['VIEWER'] };

const preview: ImportPreview = {
  previewId: 'p-1', mode: 'UPSERT_BY_CODE', totalRows: 3, validRows: 2, invalidRows: 1, toCreate: 1, toUpdate: 1,
  issues: [{ line: 3, field: 'category', message: 'Категория не найдена: NOPE' }], expiresAt: '2026-11-22T10:15:00Z',
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const fetchMock = vi.fn();
const csvFile = new File(['code;title\n'], 'orders.csv', { type: 'text/csv' });

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

async function checkFile(user: ReturnType<typeof userEvent.setup>) {
  await user.upload(screen.getByLabelText(/CSV-файл/), csvFile);
  await user.click(screen.getByLabelText('Новые и обновление по коду'));
  await user.click(screen.getByRole('button', { name: 'Проверить файл' }));
}

describe('CsvPage', () => {
  it('проверяет файл отдельным запросом и показывает счётчики и ошибки строк', async () => {
    fetchMock.mockResolvedValue(jsonResponse(preview));
    const user = userEvent.setup();
    render(<CsvPage user={editor} />);

    await checkFile(user);

    expect(await screen.findByText('Результат проверки')).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/orders/import/preview');
    const body = init.body as FormData;
    expect(body.get('mode')).toBe('UPSERT_BY_CODE');
    expect((body.get('file') as File).name).toBe('orders.csv');
    expect(within(screen.getByRole('table', { name: 'Ошибки в строках файла' })).getByText('Категория не найдена: NOPE')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Применить (2)' })).toBeEnabled();
  });

  it('применяет проверенный снимок по previewId и показывает итог', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(preview)).mockResolvedValueOnce(jsonResponse({ created: 1, updated: 1, skipped: 1 }));
    const user = userEvent.setup();
    render(<CsvPage user={editor} />);
    await checkFile(user);

    await user.click(await screen.findByRole('button', { name: 'Применить (2)' }));

    expect(await screen.findByText(/создано 1, обновлено 1, пропущено строк с ошибками 1/)).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[1];
    expect(url).toBe('/api/orders/import/apply');
    expect(JSON.parse(init.body as string)).toEqual({ previewId: 'p-1' });
    expect(screen.queryByText('Результат проверки')).not.toBeInTheDocument();
  });

  it('после конфликта версии убирает проверку и показывает ошибку сервера', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(preview)).mockResolvedValueOnce(jsonResponse({
      status: 409, code: 'VERSION_CONFLICT', message: 'Строка 3: Заказ уже изменён',
      fieldErrors: [], correlationId: 'c-9', timestamp: '2026-11-22T10:00:00Z',
    }, 409));
    const user = userEvent.setup();
    render(<CsvPage user={editor} />);
    await checkFile(user);

    await user.click(await screen.findByRole('button', { name: 'Применить (2)' }));

    expect(await screen.findByText('Строка 3: Заказ уже изменён')).toBeInTheDocument();
    expect(screen.queryByText('Результат проверки')).not.toBeInTheDocument();
  });

  it('не даёт применить файл без корректных строк', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ ...preview, validRows: 0, toCreate: 0, toUpdate: 0 }));
    const user = userEvent.setup();
    render(<CsvPage user={editor} />);
    await checkFile(user);

    expect(await screen.findByRole('button', { name: 'Применить (0)' })).toBeDisabled();
  });

  it('наблюдателю показывает только экспорт и скачивает выбранную выборку', async () => {
    fetchMock.mockResolvedValue(new Response('\uFEFFcode;title\n', {
      status: 200, headers: { 'Content-Type': 'text/csv', 'Content-Disposition': 'attachment; filename="orders-2026-11-22.csv"' },
    }));
    const createObjectURL = vi.fn(() => 'blob:orders');
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() }));
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const user = userEvent.setup();
    render(<CsvPage user={viewer} />);

    expect(screen.getByText('Импорт доступен редактору и администратору.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Проверить файл' })).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Какие заказы выгрузить'), 'all');
    await user.click(screen.getByRole('button', { name: 'Скачать CSV' }));

    await waitFor(() => expect(click).toHaveBeenCalled());
    expect(createObjectURL).toHaveBeenCalled();
    click.mockRestore();
    expect(String(fetchMock.mock.calls[0][0])).toBe('/api/orders/export?archived=all&sort=createdAt,desc');
  });
});
