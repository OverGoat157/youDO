import { useState, type FormEvent } from 'react';
import type { ApiRequestError } from '../../api/client';
import { canEdit, type Account } from '../../api/session';
import { linkProps } from '../../app/router';
import { ErrorAlert } from '../../app/ui';
import { archivedFilterLabel } from '../orders/labels';
import { formatDateTime, type ArchivedFilter } from '../orders/types';
import {
  applyImport, CSV_COLUMNS, exportOrdersCsv, MODE_LABELS, previewImport,
  type ImportMode, type ImportPreview, type ImportResult,
} from './api';
import './csv.css';

function saveBlob(blob: Blob, filename: string | null): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename ?? 'orders.csv';
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

type Busy = 'preview' | 'apply' | 'export' | null;

/** Импорт в два шага (проверка → применение) и экспорт заказов в CSV. */
export function CsvPage({ user }: { user: Account }) {
  const editable = canEdit(user);
  const [file, setFile] = useState<File | null>(null);
  const [fileKey, setFileKey] = useState(0);
  const [mode, setMode] = useState<ImportMode>('NEW_ONLY');
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [scope, setScope] = useState<ArchivedFilter>('false');
  const [busy, setBusy] = useState<Busy>(null);
  const [error, setError] = useState<ApiRequestError | Error | null>(null);

  // Любое изменение файла или режима делает прежнюю проверку неактуальной.
  // Пока идёт запрос, поля заблокированы: иначе ответ по старому файлу показался бы для нового.
  function resetCheck() {
    setPreview(null);
    setResult(null);
    setError(null);
  }

  async function check(event: FormEvent) {
    event.preventDefault();
    if (!file) return;
    setBusy('preview');
    resetCheck();
    try {
      setPreview(await previewImport(file, mode));
    } catch (cause) {
      setError(cause as ApiRequestError);
    } finally {
      setBusy(null);
    }
  }

  async function apply() {
    if (!preview || preview.invalidRows > 0) return;
    setBusy('apply');
    setError(null);
    try {
      setResult(await applyImport(preview.previewId));
      setPreview(null);
      setFile(null);
      setFileKey(value => value + 1);
    } catch (cause) {
      const failure=cause as ApiRequestError;
      setError(failure);
      if(failure.status===404 || failure.status===409) setPreview(null);
    } finally {
      // Успешный apply одноразовый; конфликт требует нового снимка, временный сбой допускает повтор.
      setBusy(null);
    }
  }

  async function download() {
    setBusy('export');
    setError(null);
    try {
      const { blob, filename } = await exportOrdersCsv(scope);
      saveBlob(blob, filename);
    } catch (cause) {
      setError(cause as ApiRequestError);
    } finally {
      setBusy(null);
    }
  }

  return (
    <main className="page" aria-labelledby="csv-title">
      <header className="page-header">
        <div>
          <h1 id="csv-title">Импорт и экспорт CSV</h1>
          <p className="muted">Файл проверяется до записи: сначала видно, что изменится, потом применяем</p>
        </div>
      </header>

      <ErrorAlert error={error} />
      {result && (
        <div className="alert alert--success" role="status">
          <p>
            Импорт выполнен: создано {result.created}, обновлено {result.updated}.
          </p>
          <p><a {...linkProps('/orders')}>Перейти к заказам</a></p>
        </div>
      )}

      <div className="csv-grid">
        <section className="card" aria-labelledby="csv-import-title">
          <h2 id="csv-import-title">Импорт</h2>
          {!editable ? (
            <p className="muted">Импорт доступен редактору и администратору.</p>
          ) : (
            <form onSubmit={check}>
              <label htmlFor="csv-file">CSV-файл (UTF-8, разделитель «;» или «,»)</label>
              <input key={fileKey} id="csv-file" type="file" accept=".csv,text/csv" disabled={busy !== null}
                onChange={event => { setFile(event.target.files?.[0] ?? null); resetCheck(); }} />

              <fieldset className="csv-modes">
                <legend>Режим</legend>
                {(Object.keys(MODE_LABELS) as ImportMode[]).map(value => (
                  <label key={value} className="csv-mode">
                    <input type="radio" name="csv-mode" value={value} checked={mode === value} disabled={busy !== null}
                      onChange={() => { setMode(value); resetCheck(); }} />
                    {MODE_LABELS[value]}
                  </label>
                ))}
              </fieldset>
              <p className="muted small">
                «Только новые» отклоняет существующий код. Во втором режиме такой заказ обновляется. До 10 000 строк и 5 MiB; перед применением исправьте все ошибки.
              </p>
              <div className="actions">
                <button type="submit" disabled={!file || busy !== null}>
                  {busy === 'preview' ? 'Проверяем…' : 'Проверить файл'}
                </button>
              </div>
            </form>
          )}
        </section>

        <section className="card" aria-labelledby="csv-export-title">
          <h2 id="csv-export-title">Экспорт</h2>
          <label htmlFor="csv-scope">Какие заказы выгрузить</label>
          <select id="csv-scope" value={scope} onChange={event => setScope(event.target.value as ArchivedFilter)}>
            {(['false', 'true', 'all'] as ArchivedFilter[]).map(value => (
              <option key={value} value={value}>{archivedFilterLabel(value)}</option>
            ))}
          </select>
          <div className="actions">
            <button type="button" className="button--secondary" disabled={busy !== null} onClick={() => void download()}>
              {busy === 'export' ? 'Готовим файл…' : 'Скачать CSV'}
            </button>
          </div>
          <p className="muted small">
            Выгрузка с поиском и фильтрами — кнопкой «Экспорт» на странице <a {...linkProps('/orders')}>«Заказы»</a>.
          </p>
        </section>
      </div>

      {preview && (
        <section className="card csv-preview" aria-labelledby="csv-preview-title">
          <h2 id="csv-preview-title">Результат проверки</h2>
          <dl className="csv-stats">
            <div><dt>Строк в файле</dt><dd>{preview.totalRows}</dd></div>
            <div><dt>Корректных</dt><dd>{preview.validRows}</dd></div>
            <div><dt>С ошибками</dt><dd>{preview.invalidRows}</dd></div>
            <div><dt>Будет создано</dt><dd>{preview.toCreate}</dd></div>
            <div><dt>Будет обновлено</dt><dd>{preview.toUpdate}</dd></div>
          </dl>

          {preview.issues.length > 0 && (
            <div className="table-scroll csv-issues" role="region" aria-label="Ошибки в строках" tabIndex={0}>
              <table>
                <caption className="visually-hidden">Ошибки в строках файла</caption>
                <thead>
                  <tr><th scope="col">Строка</th><th scope="col">Поле</th><th scope="col">Ошибка</th></tr>
                </thead>
                <tbody>
                  {preview.issues.map((issue, index) => (
                    <tr key={`${issue.line}-${issue.field}-${index}`}>
                      <td>{issue.line}</td>
                      <td className="mono">{issue.field}</td>
                      <td>{issue.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <p className="muted small">
            Режим: {MODE_LABELS[preview.mode]}. Импорт применяется целиком, без пропуска ошибочных строк.
            Если заказ изменят после проверки, импорт целиком отменится. Проверка действует до {formatDateTime(preview.expiresAt)}.
          </p>
          <div className="actions">
            <button type="button" disabled={preview.invalidRows > 0 || preview.validRows === 0 || busy !== null} onClick={() => void apply()}>
              {busy === 'apply' ? 'Применяем…' : `Применить (${preview.validRows})`}
            </button>
            <button type="button" className="button--secondary" disabled={busy !== null} onClick={resetCheck}>Отмена</button>
          </div>
        </section>
      )}

      <details className="card csv-format">
        <summary>Формат файла</summary>
        <p className="muted small">Первая строка — заголовок с этими колонками (порядок любой). Выгрузка экспорта уже в этом формате.</p>
        <table>
          <tbody>
            {CSV_COLUMNS.map(column => (
              <tr key={column.name}><td className="mono">{column.name}</td><td>{column.hint}</td></tr>
            ))}
          </tbody>
        </table>
      </details>
    </main>
  );
}
