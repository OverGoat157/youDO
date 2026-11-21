import { useEffect, useRef, useState } from 'react';
import { apiFetch, getAccessToken } from '../../api/client';
import { ErrorAlert } from '../../app/ui';

interface SwaggerRequest { url: string; headers: Record<string, string>; credentials?: string }
interface SwaggerInstance { destroy?: () => void }
interface SwaggerBundle {
  (options: { spec: unknown; domNode: HTMLElement; presets: unknown[];
    requestInterceptor: (request: SwaggerRequest) => Promise<SwaggerRequest> }): SwaggerInstance;
  presets: { apis: unknown };
}
declare global { interface Window { SwaggerUIBundle?: SwaggerBundle } }
let bundleLoading: Promise<SwaggerBundle> | null = null;

function loadBundle(): Promise<SwaggerBundle> {
  if (window.SwaggerUIBundle) return Promise.resolve(window.SwaggerUIBundle);
  if (!bundleLoading) {
    bundleLoading = apiFetch<string>('/swagger-ui/swagger-ui-bundle.js').then(source => new Promise<SwaggerBundle>((resolve, reject) => {
      // Load the server's protected Swagger asset using the current admin's Bearer token.
      const url = URL.createObjectURL(new Blob([source], { type: 'text/javascript' }));
      const script = document.createElement('script');
      script.src = url;
      script.onload = () => {
        URL.revokeObjectURL(url);
        if (window.SwaggerUIBundle) resolve(window.SwaggerUIBundle);
        else reject(new Error('Не удалось загрузить Swagger UI'));
      };
      script.onerror = () => { URL.revokeObjectURL(url); script.remove(); reject(new Error('Не удалось загрузить Swagger UI')); };
      document.head.appendChild(script);
    })).catch(error => { bundleLoading = null; throw error; });
  }
  return bundleLoading;
}

/** Both schema and UI assets remain protected by ADMIN on the server. Tokens stay in memory. */
export function DocsPage() {
  const node = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    let cancelled = false;
    let style: HTMLStyleElement | null = null;
    let ui: SwaggerInstance | null = null;
    setLoading(true); setError(null);
    Promise.all([loadBundle(), apiFetch<unknown>('/v3/api-docs'), apiFetch<string>('/swagger-ui/swagger-ui.css')])
      .then(([bundle, spec, css]) => {
        if (cancelled || !node.current) return;
        style = document.createElement('style'); style.textContent = css; document.head.appendChild(style);
        ui = bundle({ spec, domNode: node.current, presets: [bundle.presets.apis], requestInterceptor: async request => {
          const url = new URL(request.url, window.location.origin);
          if (url.origin !== window.location.origin) throw new Error('API доступен на текущем сервере');
          // Refresh through the shared client before executing a request after a long idle period.
          await apiFetch('/api/auth/me');
          const token = getAccessToken();
          if (token) request.headers.Authorization = `Bearer ${token}`;
          request.credentials = 'include';
          return request;
        } });
      })
      .catch(cause => { if (!cancelled) setError(cause as Error); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; style?.remove(); ui?.destroy?.(); };
  }, [revision]);
  return <main className="page" aria-labelledby="docs-title">
    <h1 id="docs-title">REST API · Swagger</h1>
    <p className="muted">Документация и выполнение запросов с правами текущего администратора.</p>
    <ErrorAlert error={error} onRetry={() => setRevision(value => value + 1)} />
    {loading && <p role="status">Загружаем документацию…</p>}
    <div ref={node} />
  </main>;
}
