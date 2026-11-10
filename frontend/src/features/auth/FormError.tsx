import type { ApiError } from './contracts';

export function FormError({ error, login = false }: { error: ApiError | null; login?: boolean }) {
  if (!error) return null;
  const message = login
    ? error.status === 429 ? 'Слишком много попыток. Повторите вход позже.'
      : error.status && error.status >= 500 ? 'Сервис недоступен. Повторите позже.'
      : error.status === 401 || error.status === 403 ? 'Не удалось войти. Проверьте логин и пароль.'
      : 'Не удалось войти. Проверьте соединение и введённые данные.'
    : error.message || 'Не удалось выполнить запрос. Повторите попытку.';
  return <div className="alert alert--error" role="alert">
    <p>{message}</p>
    {!login && !!error.fieldErrors?.length && <ul>{error.fieldErrors.map((e, i) => <li key={i}>{e.field}: {e.message}</li>)}</ul>}
    {error.correlationId && <small>Код обращения: {error.correlationId}</small>}
  </div>;
}
