import { useEffect, useState } from 'react';
import type { ApiRequestError } from '../../api/client';
import { ErrorAlert } from '../../app/ui';
import {
  formatBudget, formatDeadline, PRIORITY_LABELS, STATUS_LABELS, type OrderPriority, type OrderStatus,
} from '../orders/types';
import { getSummary, type AnalyticsGroup, type AnalyticsSummary } from './api';
import './analytics.css';

/** Таблица группы с полосой доли: число видно всегда, полоса — для быстрого сравнения. */
function GroupTable({ title, groups, label }: { title: string; groups: AnalyticsGroup[]; label: (group: AnalyticsGroup) => string }) {
  const max = Math.max(1, ...groups.map(group => group.count));
  return (
    <section className="card analytics-group" aria-label={title}>
      <h2>{title}</h2>
      {groups.length === 0 ? (
        <p className="muted">Активных заказов нет.</p>
      ) : (
        <table>
          <tbody>
            {groups.map(group => (
              <tr key={group.key}>
                <th scope="row">{label(group)}</th>
                <td className="analytics-bar-cell">
                  <span className="analytics-bar" style={{ width: `${(group.count / max) * 100}%` }} aria-hidden="true" />
                </td>
                <td className="analytics-count">{group.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

/** Сводка по заказам: счётчики и группировки по категории, статусу и вычисляемому приоритету. */
export function AnalyticsPage() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiRequestError | Error | null>(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    getSummary(controller.signal)
      .then(setSummary)
      .catch(cause => {
        if (cause instanceof DOMException && cause.name === 'AbortError') return;
        setError(cause as ApiRequestError);
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [revision]);

  const tiles = summary ? [
    { label: 'Всего заказов', value: String(summary.total) },
    { label: 'Активные', value: String(summary.active) },
    { label: 'В архиве', value: String(summary.archived) },
    { label: 'Срочные среди активных', value: String(summary.urgent) },
    { label: 'Средний бюджет активных', value: summary.averageBudget === null ? '—' : formatBudget(String(summary.averageBudget)) },
  ] : [];

  return (
    <main className="page" aria-labelledby="analytics-title">
      <header className="page-header">
        <div>
          <h1 id="analytics-title">Аналитика</h1>
          <p className="muted">Группировки и средний бюджет считаются по активным заказам, без архива</p>
        </div>
        <button type="button" className="button--secondary" disabled={loading} onClick={() => setRevision(value => value + 1)}>
          Обновить
        </button>
      </header>

      <ErrorAlert error={error} onRetry={() => setRevision(value => value + 1)} />
      {loading && <p role="status" className="muted">Считаем сводку…</p>}

      {summary && (
        <>
          <dl className="analytics-tiles">
            {tiles.map(tile => (
              <div key={tile.label} className="card analytics-tile">
                <dt>{tile.label}</dt>
                <dd>{tile.value}</dd>
              </div>
            ))}
          </dl>

          <div className="analytics-groups">
            <GroupTable title="По категориям" groups={summary.byCategory} label={group => group.name} />
            <GroupTable title="По статусам" groups={summary.byStatus}
              label={group => STATUS_LABELS[group.key as OrderStatus] ?? group.key} />
            <GroupTable title="По приоритету" groups={summary.byPriority}
              label={group => PRIORITY_LABELS[group.key as OrderPriority] ?? group.key} />
          </div>
          <p className="muted small">
            Приоритет вычисляется на {formatDeadline(summary.priorityDate)}: срочные и просроченные — высокий,
            дедлайн в ближайшие 3 дня — средний, завершённые и отменённые — низкий.
          </p>
        </>
      )}
    </main>
  );
}
