import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiRequestError, apiFetch, jsonRequest } from '../../api/client';
import { isAdmin, type Account } from '../../api/session';
import { listCategories } from '../orders/api';
import type { Category } from '../orders/types';
import { ErrorAlert } from '../../app/ui';
import './categories.css';

interface CategoryWrite {
  code: string;
  name: string;
  active: boolean;
}

type Editor = { kind: 'create' } | { kind: 'edit'; category: Category };

const CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,49}$/;

export function CategoriesPage({ user }: { user: Account }) {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [revision, setRevision] = useState(0);
  const [editor, setEditor] = useState<Editor | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const admin = isAdmin(user);
  const createButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    // Справочник читается целиком: отключённые категории остаются видимыми в старых заказах.
    listCategories(controller.signal)
      .then(setCategories)
      .catch(cause => {
        if (cause instanceof DOMException && cause.name === 'AbortError') return;
        setError(cause as ApiRequestError);
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [revision]);

  function close(saved = false) {
    setEditor(null);
    if (saved) {
      setNotice('Изменения справочника сохранены.');
      setRevision(value => value + 1);
    }
    requestAnimationFrame(() => createButton.current?.focus());
  }

  return (
    <main className="page" aria-labelledby="categories-title">
      <header className="page-header">
        <div>
          <h1 id="categories-title">Категории</h1>
          <p className="muted">Справочник заказов: список доступен всем, изменение — администратору</p>
        </div>
        {admin && <button ref={createButton} type="button" onClick={() => setEditor({ kind: 'create' })} disabled={!!editor}>Создать категорию</button>}
      </header>

      {notice && <div className="alert alert--success" role="status"><p>{notice}</p></div>}
      {!admin && <p className="alert">Создание и изменение категорий доступно только роли ADMIN. Отключённую категорию можно видеть в старом заказе, но нельзя назначить новую.</p>}
      <ErrorAlert error={error} onRetry={() => setRevision(value => value + 1)} />
      {editor && <CategoryEditor editor={editor} categories={categories} onClose={close} />}
      {loading && <p role="status" className="muted">Загружаем справочник…</p>}
      {!loading && !error && categories.length === 0 && <p className="empty-state">Справочник пуст. Администратор может добавить первую категорию.</p>}

      {categories.length > 0 && (
        <div className="table-scroll" role="region" aria-label="Справочник категорий" tabIndex={0}>
          <table>
            <caption className="visually-hidden">Категории заказов</caption>
            <thead><tr><th scope="col">Название</th><th scope="col">Код</th><th scope="col">Статус</th>{admin && <th scope="col">Действия</th>}</tr></thead>
            <tbody>
              {categories.map(category => (
                <tr key={category.id}>
                  <td><strong>{category.name}</strong></td>
                  <td className="mono">{category.code}</td>
                  <td>
                    <span className={`badge ${category.active ? 'badge--active' : 'badge--inactive'}`}>
                      {category.active ? 'Доступна' : 'Отключена'}
                    </span>
                  </td>
                  {admin && (
                    <td>
                      <button type="button" className="button--secondary" disabled={!!editor}
                        aria-label={`Изменить категорию ${category.name}`}
                        onClick={() => setEditor({ kind: 'edit', category })}>
                        Изменить
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <p className="muted small">Категории не удаляются: администратор меняет название, код и признак «доступна». Уже назначенную отключённую категорию заказ сохраняет.</p>
    </main>
  );
}

function CategoryEditor({ editor, categories, onClose }: {
  editor: Editor;
  categories: Category[];
  onClose: (saved?: boolean) => void;
}) {
  const category = editor.kind === 'edit' ? editor.category : null;
  const [code, setCode] = useState(category?.code ?? '');
  const [name, setName] = useState(category?.name ?? '');
  const [active, setActive] = useState(category?.active ?? true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const pending = useRef(false);
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus(); }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (pending.current) return;
    const errors: Record<string, string> = {};
    if (!CODE_PATTERN.test(code.trim())) errors.code = 'Код: латинские буквы, цифры и «_», начинается с буквы, до 50 символов';
    if (!name.trim()) errors.name = 'Укажите название';
    const duplicate = categories.find(item => item.code.toUpperCase() === code.trim().toUpperCase() && item.id !== category?.id);
    if (duplicate) errors.code = 'Такой код уже используется';
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    pending.current = true;
    setBusy(true);
    setError(null);
    const body: CategoryWrite = { code: code.trim().toUpperCase(), name: name.trim(), active };
    try {
      if (category) {
        await apiFetch<Category>(`/api/categories/${category.id}`, jsonRequest('PUT', body));
      } else {
        await apiFetch<Category>('/api/categories', jsonRequest('POST', body));
      }
      onClose(true);
    } catch (cause) {
      const api = cause as ApiRequestError;
      setError(api);
      const mapped: Record<string, string> = {};
      for (const item of api.fieldErrors ?? []) mapped[item.field] = item.message;
      setFieldErrors(mapped);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  return (
    <section className="card category-editor" aria-labelledby="category-editor-title">
      <h2 id="category-editor-title" ref={heading} tabIndex={-1}>{category ? 'Изменить категорию' : 'Новая категория'}</h2>
      {error && <div className="alert alert--error" role="alert"><p>{error.message}</p>{error.correlationId && <small>Код обращения: {error.correlationId}</small>}</div>}
      <form onSubmit={submit} aria-busy={busy} noValidate>
        <label htmlFor="category-name">Название</label>
        <input id="category-name" required maxLength={100} value={name}
          aria-invalid={Boolean(fieldErrors.name)} onChange={event => setName(event.target.value)} disabled={busy} />
        {fieldErrors.name && <p className="field-error">{fieldErrors.name}</p>}
        <label htmlFor="category-code">Код</label>
        <input id="category-code" required maxLength={50} className="mono" value={code}
          aria-invalid={Boolean(fieldErrors.code)} onChange={event => setCode(event.target.value)} disabled={busy} />
        {fieldErrors.code && <p className="field-error">{fieldErrors.code}</p>}
        <label className="check-label field">
          <input type="checkbox" checked={active} onChange={event => setActive(event.target.checked)} disabled={busy} />
          Категория доступна для новых заказов
        </label>
        <div className="actions">
          <button type="submit" disabled={busy}>{busy ? 'Сохраняем…' : 'Сохранить'}</button>
          <button type="button" className="button--secondary" disabled={busy} onClick={() => onClose()}>Отмена</button>
        </div>
      </form>
    </section>
  );
}
