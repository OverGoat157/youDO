import { useSyncExternalStore, type MouseEvent } from 'react';

const listeners = new Set<() => void>();

function emit(): void {
  for (const listener of listeners) listener();
}

if (typeof window !== 'undefined') {
  window.addEventListener('popstate', emit);
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

function snapshot(): string {
  return typeof window === 'undefined' ? '/' : window.location.pathname + window.location.search;
}

export function useLocationPath(): string {
  return useSyncExternalStore(subscribe, snapshot, () => '/');
}

export interface NavigateOptions {
  replace?: boolean;
}

export function navigate(path: string, options: NavigateOptions = {}): void {
  if (typeof window === 'undefined') return;
  if (options.replace) window.history.replaceState(null, '', path);
  else window.history.pushState(null, '', path);
  emit();
}

export function linkProps(path: string): {
  href: string;
  onClick: (event: MouseEvent<HTMLAnchorElement>) => void;
} {
  return {
    href: path,
    onClick: (event) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      navigate(path);
    },
  };
}

export type RouteParams = Record<string, string>;

export function matchPath(pattern: string, path: string): RouteParams | null {
  const clean = path.split('?')[0].replace(/\/+$/, '') || '/';
  if (pattern === clean) return {};
  const patternParts = pattern.split('/');
  const pathParts = clean.split('/');
  if (patternParts.length !== pathParts.length) return null;
  const params: RouteParams = {};
  for (let index = 0; index < patternParts.length; index += 1) {
    const expected = patternParts[index];
    const actual = pathParts[index];
    if (expected.startsWith(':')) {
      if (!actual) return null;
      params[expected.slice(1)] = decodeURIComponent(actual);
    } else if (expected !== actual) {
      return null;
    }
  }
  return params;
}

export function currentPath(): string {
  return snapshot();
}
