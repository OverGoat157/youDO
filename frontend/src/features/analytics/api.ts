import { apiFetch } from '../../api/client';

export interface AnalyticsGroup {
  key: string;
  name: string;
  count: number;
}

/** total/active/archived — все заказы; urgent, averageBudget и группы — только активные. */
export interface AnalyticsSummary {
  total: number;
  active: number;
  archived: number;
  urgent: number;
  averageBudget: string | number | null;
  byCategory: AnalyticsGroup[];
  byStatus: AnalyticsGroup[];
  byPriority: AnalyticsGroup[];
  priorityDate: string;
}

export function getSummary(signal?: AbortSignal): Promise<AnalyticsSummary> {
  return apiFetch<AnalyticsSummary>('/api/analytics/summary', { signal });
}
