import { describe, expect, it } from 'vitest';
import { buildOrderParams } from './api';
import { EMPTY_FILTER, normalizeTags, type OrderQuery } from './types';

const base: OrderQuery = {
  ...EMPTY_FILTER, page: 2, size: 50, sortField: 'budget', sortDirection: 'desc',
};

describe('buildOrderParams', () => {
  it('не отправляет пустые условия и передаёт страницу, размер и сортировку', () => {
    const params = buildOrderParams(base);
    expect(params.get('q')).toBeNull();
    expect(params.get('archived')).toBe('false');
    expect(params.get('page')).toBe('2');
    expect(params.get('size')).toBe('50');
    expect(params.get('sort')).toBe('budget,desc');
  });

  it('объединяет текст, фильтры и сравнение бюджета', () => {
    const params = buildOrderParams({
      ...base,
      q: ' лендинг ',
      status: 'OPEN',
      categoryId: 'c-1',
      tag: 'backend',
      urgent: 'true',
      priority: 'HIGH',
      archived: 'all',
      budgetFrom: ' 1000 ',
      budgetTo: '5000',
    });
    expect(params.get('q')).toBe('лендинг');
    expect(params.get('status')).toBe('OPEN');
    expect(params.get('categoryId')).toBe('c-1');
    expect(params.get('tag')).toBe('backend');
    expect(params.get('urgent')).toBe('true');
    expect(params.get('priority')).toBe('HIGH');
    expect(params.get('archived')).toBe('all');
    expect(params.get('budgetFrom')).toBe('1000');
    expect(params.get('budgetTo')).toBe('5000');
  });

  it('выражает одну границу бюджета общим договором budgetOp/budgetValue', () => {
    const fromOnly = buildOrderParams({ ...base, budgetFrom: '1000' });
    expect(fromOnly.get('budgetOp')).toBe('gte');
    expect(fromOnly.get('budgetValue')).toBe('1000');
    expect(fromOnly.get('budgetFrom')).toBeNull();

    const toOnly = buildOrderParams({ ...base, budgetTo: '5000' });
    expect(toOnly.get('budgetOp')).toBe('lte');
    expect(toOnly.get('budgetValue')).toBe('5000');
  });

  it('не отправляет пустой бюджет', () => {
    const params = buildOrderParams(base);
    expect(params.get('budgetOp')).toBeNull();
    expect(params.get('budgetValue')).toBeNull();
    expect(params.get('budgetFrom')).toBeNull();
  });

  it('для экспорта оставляет условия, но не страницу', () => {
    const params = buildOrderParams({ ...base, status: 'COMPLETED' }, false);
    expect(params.get('status')).toBe('COMPLETED');
    expect(params.get('page')).toBeNull();
    expect(params.get('size')).toBeNull();
    expect(params.get('sort')).toBe('budget,desc');
  });
});

describe('normalizeTags', () => {
  it('убирает повторы и приводит к нижнему регистру', () => {
    expect(normalizeTags('Backend, backend ,, Верстка')).toEqual(['backend', 'верстка']);
  });

  it('ограничивает количество тегов двадцатью', () => {
    const tags = Array.from({ length: 25 }, (_, index) => `tag${index}`).join(',');
    expect(normalizeTags(tags)).toHaveLength(20);
  });
});
