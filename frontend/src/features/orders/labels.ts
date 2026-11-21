import type { ArchivedFilter } from './types';

export function archivedFilterLabel(value: ArchivedFilter): string {
  if (value === 'true') return 'Только архивные';
  if (value === 'all') return 'Все, включая архив';
  return 'Только активные';
}
