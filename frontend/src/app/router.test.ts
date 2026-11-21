import { describe, expect, it } from 'vitest';
import { matchPath } from './router';

describe('matchPath', () => {
  it('сопоставляет статические маршруты', () => {
    expect(matchPath('/orders', '/orders')).toEqual({});
    expect(matchPath('/orders', '/orders/')).toEqual({});
  });

  it('выделяет параметры пути и игнорирует query', () => {
    expect(matchPath('/orders/:id', '/orders/abc?tab=notes')).toEqual({ id: 'abc' });
    expect(matchPath('/orders/:id/edit', '/orders/abc/edit')).toEqual({ id: 'abc' });
  });

  it('не путает карточку с созданием и не совпадает по числу сегментов', () => {
    expect(matchPath('/orders/:id', '/orders/new')).toEqual({ id: 'new' });
    expect(matchPath('/orders/:id/edit', '/orders/abc')).toBeNull();
    expect(matchPath('/orders/new', '/orders/abc')).toBeNull();
  });
});
