import { useEffect } from 'react';
import type { ApiRequestError } from '../api/client';
import { navigate } from './router';

export function Redirect({ to }: { to: string }) {
  useEffect(() => { navigate(to, { replace: true }); }, [to]);
  return <p role="status" className="page">Перенаправление…</p>;
}

export function LoadingScreen({ label = 'Загружаем данные…' }: { label?: string }) {
  return <div className="page" role="status" aria-live="polite"><p className="muted">{label}</p></div>;
}

export function ErrorAlert({ error, onRetry }: { error: ApiRequestError | Error | null; onRetry?: () => void }) {
  if (!error) return null;
  const api = error as ApiRequestError;
  return (
    <div className="alert alert--error" role="alert">
      <p>{error.message || 'Не удалось выполнить запрос.'}</p>
      {!!api.fieldErrors?.length && (
        <ul>
          {api.fieldErrors.map(item => <li key={`${item.field}-${item.message}`}>{item.field}: {item.message}</li>)}
        </ul>
      )}
      {api.correlationId && <small>Код обращения: {api.correlationId}</small>}
      {onRetry && <p><button type="button" className="button--secondary" onClick={onRetry}>Повторить загрузку</button></p>}
    </div>
  );
}

export function ForbiddenPage({ message }: { message?: string }) {
  return (
    <main className="page" aria-labelledby="forbidden-title">
      <h1 id="forbidden-title">Доступ ограничен</h1>
      <p role="alert">{message ?? 'Раздел доступен только администратору.'}</p>
      <p><a href="/orders" onClick={event => { event.preventDefault(); navigate('/orders'); }}>Вернуться к заказам</a></p>
    </main>
  );
}

export function UnavailablePage({ title }: { title: string }) {
  return (
    <main className="page" aria-labelledby="unavailable-title">
      <h1 id="unavailable-title">{title}</h1>
      <p className="muted">Раздел пока недоступен.</p>
      <p><a href="/orders" onClick={event => { event.preventDefault(); navigate('/orders'); }}>Перейти к заказам</a></p>
    </main>
  );
}

export function NotFoundPage() {
  return (
    <main className="page" aria-labelledby="not-found-title">
      <h1 id="not-found-title">Страница не найдена</h1>
      <p>Проверьте адрес: такой страницы в приложении нет.</p>
      <p><a href="/orders" onClick={event => { event.preventDefault(); navigate('/orders'); }}>Перейти к заказам</a></p>
    </main>
  );
}

