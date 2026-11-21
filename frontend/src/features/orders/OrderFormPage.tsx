import { useEffect, useRef, useState, type FormEvent } from 'react';
import { linkProps, navigate } from '../../app/router';
import { ApiRequestError } from '../../api/client';
import { canEdit, type Account } from '../../api/session';
import { ForbiddenPage } from '../../app/ui';
import {
  createOrder, getOrder, listCategories, listParticipants, updateOrder,
} from './api';
import {
  STATUS_LABELS, STATUS_TRANSITIONS, formatDeadline, normalizeTags,
  type Category, type OrderResponse, type OrderStatus, type Participant,
} from './types';
import './orders.css';

export interface OrderFormPageProps {
  user: Account;
  orderId?: string;
}

interface FormState {
  code: string;
  title: string;
  description: string;
  categoryId: string;
  status: OrderStatus;
  budget: string;
  deadline: string;
  urgent: boolean;
  tags: string;
}

const EMPTY_FORM: FormState = {
  code: '', title: '', description: '', categoryId: '', status: 'OPEN',
  budget: '', deadline: '', urgent: false, tags: '',
};

const BUDGET_PATTERN = /^\d{1,10}(\.\d{1,2})?$/;
const CODE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_-]{0,49}$/;

function today(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

export function OrderFormPage({ user, orderId }: OrderFormPageProps) {
  const editing = Boolean(orderId);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [customer, setCustomer] = useState<Participant | null>(null);
  const [freelancer, setFreelancer] = useState<Participant | null>(null);
  const [version, setVersion] = useState<number | null>(null);
  const [current, setCurrent] = useState<OrderResponse | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(editing);
  const [loadError, setLoadError] = useState<ApiRequestError | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [conflict, setConflict] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const pending = useRef(false);
  const titleRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    listCategories(controller.signal)
      .then(setCategories)
      .catch(cause => {
        if (cause instanceof DOMException && cause.name === 'AbortError') return;
        setCategories([]);
      });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!orderId) return;
    const controller = new AbortController();
    setLoading(true);
    setLoadError(null);
    getOrder(orderId, controller.signal)
      .then(order => {
        setCurrent(order);
        setVersion(order.version);
        setForm({
          code: order.code,
          title: order.title,
          description: order.description,
          categoryId: order.categoryId ?? '',
          status: order.status,
          budget: order.budget,
          deadline: order.deadline,
          urgent: order.urgent,
          tags: order.tags.join(', '),
        });
        setCustomer({ id: order.customerId, name: order.customerName, email: '', role: 'CUSTOMER' });
        setFreelancer(order.freelancerId === null
          ? null
          : { id: order.freelancerId, name: order.freelancerName ?? '', email: '', role: 'FREELANCER' });
      })
      .catch(cause => {
        if (cause instanceof DOMException && cause.name === 'AbortError') return;
        setLoadError(cause as ApiRequestError);
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [orderId]);

  if (!canEdit(user)) {
    return <ForbiddenPage message="Создание и изменение заказов доступны ролям EDITOR и ADMIN. Просмотр заказов открыт всем авторизованным." />;
  }

  if (loading) return <main className="page"><p role="status" className="muted">Загружаем заказ…</p></main>;

  if (loadError) {
    return (
      <main className="page">
        <h1>{loadError.status === 404 ? 'Заказ не найден' : 'Не удалось загрузить заказ'}</h1>
        <div className="alert alert--error" role="alert"><p>{loadError.message}</p></div>
        <p><a {...linkProps('/orders')}>Вернуться к списку</a></p>
      </main>
    );
  }

  if (editing && current?.archived) {
    return (
      <main className="page">
        <h1>Заказ в архиве</h1>
        <p>Изменение архивного заказа недоступно. Сначала восстановите его из архива в карточке.</p>
        <p><a {...linkProps(`/orders/${current.id}`)}>Открыть карточку</a></p>
      </main>
    );
  }

  const selectableStatuses: OrderStatus[] = editing && current
    ? [current.status, ...STATUS_TRANSITIONS[current.status]]
    : ['OPEN'];

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm(previous => ({ ...previous, [key]: value }));
  }

  function validate(): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!form.title.trim()) errors.title = 'Укажите название заказа';
    else if (form.title.length > 150) errors.title = 'Название — не более 150 символов';
    if (!CODE_PATTERN.test(form.code.trim())) errors.code = 'Код: латинские буквы, цифры, «-» и «_», до 50 символов';
    if (form.description.length > 5000) errors.description = 'Описание — не более 5000 символов';
    if (!form.categoryId) errors.categoryId = 'Выберите категорию';
    if (!BUDGET_PATTERN.test(form.budget.trim()) || Number(form.budget) <= 0) {
      errors.budget = 'Бюджет больше нуля, до 9999999999.99, не более двух знаков после запятой';
    }
    if (!form.deadline) errors.deadline = 'Укажите дедлайн';
    else if (form.deadline < today()) errors.deadline = 'Дедлайн не может быть в прошлом';
    if (!customer) errors.customerId = 'Выберите заказчика';
    if (customer && freelancer && String(customer.id) === String(freelancer.id)) {
      errors.freelancerId = 'Заказчик не может выполнять свой заказ';
    }
    if (editing && form.status === 'IN_PROGRESS' && !freelancer) {
      errors.freelancerId = 'Начать работу без исполнителя нельзя';
    }
    if (form.tags.length > 0 && normalizeTags(form.tags).length === 0) errors.tags = 'Проверьте формат тегов';
    return errors;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (pending.current) return;
    setError(null);
    setConflict(false);
    const clientErrors = validate();
    setFieldErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) {
      titleRef.current?.focus();
      return;
    }
    pending.current = true;
    setBusy(true);
    const payload = {
      code: form.code.trim(),
      title: form.title.trim(),
      description: form.description.trim(),
      categoryId: form.categoryId,
      budget: form.budget.trim(),
      deadline: form.deadline,
      customerId: customer!.id,
      freelancerId: freelancer?.id ?? null,
      urgent: form.urgent,
      tags: normalizeTags(form.tags),
    };
    try {
      if (editing && orderId && version !== null) {
        const saved = await updateOrder(orderId, { ...payload, status: form.status, version });
        navigate(`/orders/${saved.id}`, { replace: true });
      } else {
        const saved = await createOrder(payload);
        navigate(`/orders/${saved.id}`, { replace: true });
      }
    } catch (cause) {
      const api = cause as ApiRequestError;
      setError(api);
      const mapped: Record<string, string> = {};
      for (const item of api.fieldErrors ?? []) mapped[item.field] = item.message;
      setFieldErrors(mapped);
      if (api.status === 409) setConflict(true);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  const selectedCategory = categories.find(item => item.id === form.categoryId);
  const disabledCategoryKept = selectedCategory ? !selectedCategory.active : false;

  return (
    <main className="page" aria-labelledby="form-title">
      <p className="breadcrumbs">
        <a {...linkProps('/orders')}>Заказы</a>
        {editing && current && <> / <a {...linkProps(`/orders/${current.id}`)}>{current.code}</a></>}
        {' '}/ {editing ? 'изменение' : 'новый заказ'}
      </p>
      <header className="page-header">
        <div>
          <h1 id="form-title">{editing ? 'Изменение заказа' : 'Новый заказ'}</h1>
          <p className="muted">Заполните условия заказа и выберите участников</p>
        </div>
      </header>

      {error && (
        <div className={`alert ${conflict ? 'alert--error' : 'alert--error'}`} role="alert">
          <p>{conflict
            ? 'Заказ уже изменён другим пользователем (конфликт версии). Введённые данные сохранены в форме и не перезаписывают чужие изменения.'
            : error.message}</p>
          {!!Object.keys(fieldErrors).length && (
            <ul>{Object.entries(fieldErrors).map(([field, message]) => <li key={field}>{field}: {message}</li>)}</ul>
          )}
          {error.correlationId && <small>Код обращения: {error.correlationId}</small>}
          {conflict && current && (
            <p>
              <a href={`/orders/${current.id}`} target="_blank" rel="noreferrer">
                Открыть карточку в новой вкладке, чтобы увидеть актуальную версию
              </a>
              {' '}— форма с вашим вводом останется открытой. Автоматическое повторное сохранение не выполняется.
            </p>
          )}
        </div>
      )}

      <form className="card order-form" onSubmit={submit} aria-busy={busy} noValidate>
        <div className="form-grid">
          <div className={`field field--wide${fieldErrors.title ? ' field--invalid' : ''}`}>
            <label htmlFor="order-title">Название</label>
            <input id="order-title" ref={titleRef} required maxLength={150} value={form.title}
              aria-invalid={Boolean(fieldErrors.title)} aria-describedby={fieldErrors.title ? 'error-title' : undefined}
              onChange={event => update('title', event.target.value)} disabled={busy} />
            {fieldErrors.title && <p className="field-error" id="error-title">{fieldErrors.title}</p>}
          </div>
          <div className={`field${fieldErrors.code ? ' field--invalid' : ''}`}>
            <label htmlFor="order-code">Код заказа</label>
            <input id="order-code" required maxLength={50} value={form.code} className="mono"
              aria-invalid={Boolean(fieldErrors.code)} aria-describedby={fieldErrors.code ? 'error-code' : 'code-help'}
              onChange={event => update('code', event.target.value)} disabled={busy} />
            <p className="muted small" id="code-help">Уникальный код, например WEB-14</p>
            {fieldErrors.code && <p className="field-error" id="error-code">{fieldErrors.code}</p>}
          </div>
          <div className={`field${fieldErrors.categoryId ? ' field--invalid' : ''}`}>
            <label htmlFor="order-category">Категория</label>
            <select id="order-category" required value={form.categoryId}
              aria-invalid={Boolean(fieldErrors.categoryId)}
              onChange={event => update('categoryId', event.target.value)} disabled={busy}>
              <option value="">Выберите категорию</option>
              {categories.map(category => (
                <option key={category.id} value={category.id} disabled={!category.active && category.id !== form.categoryId}>
                  {category.name}{category.active ? '' : ' (отключена)'}
                </option>
              ))}
            </select>
            {disabledCategoryKept && (
              <p className="muted small">Категория отключена в справочнике. Её можно оставить, но нельзя назначить новому заказу.</p>
            )}
            {fieldErrors.categoryId && <p className="field-error">{fieldErrors.categoryId}</p>}
          </div>
          <div className={`field${fieldErrors.status ? ' field--invalid' : ''}`}>
            <label htmlFor="order-status">Статус</label>
            <select id="order-status" value={form.status} disabled={!editing || busy}
              onChange={event => update('status', event.target.value as OrderStatus)}>
              {selectableStatuses.map(status => <option key={status} value={status}>{STATUS_LABELS[status]}</option>)}
            </select>
            {fieldErrors.status && <p className="field-error">{fieldErrors.status}</p>}
          </div>
          <div className={`field${fieldErrors.budget ? ' field--invalid' : ''}`}>
            <label htmlFor="order-budget">Бюджет, ₽</label>
            <input id="order-budget" inputMode="decimal" required value={form.budget}
              aria-invalid={Boolean(fieldErrors.budget)}
              onChange={event => update('budget', event.target.value.replace(',', '.'))} disabled={busy} />
            {fieldErrors.budget && <p className="field-error">{fieldErrors.budget}</p>}
          </div>
          <div className={`field${fieldErrors.deadline ? ' field--invalid' : ''}`}>
            <label htmlFor="order-deadline">Дедлайн</label>
            <input id="order-deadline" type="date" required min={today()} value={form.deadline}
              aria-invalid={Boolean(fieldErrors.deadline)}
              onChange={event => update('deadline', event.target.value)} disabled={busy} />
            {fieldErrors.deadline && <p className="field-error">{fieldErrors.deadline}</p>}
          </div>
          <ParticipantPicker
            id="order-customer"
            role="CUSTOMER"
            label="Заказчик"
            value={customer}
            disabled={busy || editing}
            error={fieldErrors.customerId}
            onChange={setCustomer}
          />
          <ParticipantPicker
            id="order-freelancer"
            role="FREELANCER"
            label="Исполнитель"
            value={freelancer}
            disabled={busy}
            error={fieldErrors.freelancerId}
            onChange={setFreelancer}
            optional
          />
          <div className="field field--wide">
            <label htmlFor="order-description">Описание</label>
            <textarea id="order-description" rows={6} maxLength={5000} value={form.description}
              onChange={event => update('description', event.target.value)} disabled={busy} />
            {fieldErrors.description && <p className="field-error">{fieldErrors.description}</p>}
          </div>
          <div className={`field${fieldErrors.tags ? ' field--invalid' : ''}`}>
            <label htmlFor="order-tags">Теги</label>
            <input id="order-tags" value={form.tags} placeholder="лендинг, срочно"
              onChange={event => update('tags', event.target.value)} disabled={busy} />
            <p className="muted small">Через запятую</p>
            {fieldErrors.tags && <p className="field-error">{fieldErrors.tags}</p>}
          </div>
          <label className="check-label field">
            <input type="checkbox" checked={form.urgent} onChange={event => update('urgent', event.target.checked)} disabled={busy} />
            Срочный заказ (влияет на вычисляемый приоритет)
          </label>
        </div>
        <div className="actions">
          <button type="submit" disabled={busy}>{busy ? 'Сохраняем…' : editing ? 'Сохранить изменения' : 'Создать заказ'}</button>
          <button type="button" className="button--secondary" disabled={busy}
            onClick={() => navigate(editing && current ? `/orders/${current.id}` : '/orders')}>
            Отмена
          </button>
          {editing && current && <span className="muted small">Текущий дедлайн: {formatDeadline(current.deadline)}</span>}
        </div>
      </form>
    </main>
  );
}

interface ParticipantPickerProps {
  id: string;
  role: 'CUSTOMER' | 'FREELANCER';
  label: string;
  value: Participant | null;
  disabled?: boolean;
  optional?: boolean;
  error?: string;
  onChange: (value: Participant | null) => void;
}

function ParticipantPicker({ id, role, label, value, disabled, optional, error, onChange }: ParticipantPickerProps) {
  const [query, setQuery] = useState('');
  const [options, setOptions] = useState<Participant[]>([]);
  const [loading, setLoading] = useState(false);
  const [failure, setFailure] = useState<ApiRequestError | null>(null);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open || disabled) return;
    const controller = new AbortController();
    setLoading(true);
    setFailure(null);
    const timer = window.setTimeout(() => {
      listParticipants({ role, q: query, size: 10 }, controller.signal)
        .then(data => setOptions(data.content ?? []))
        .catch(cause => {
          if (cause instanceof DOMException && cause.name === 'AbortError') return;
          setFailure(cause as ApiRequestError);
        })
        .finally(() => setLoading(false));
    }, 250);
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [open, query, role, disabled]);

  return (
    <div className={`field participant-picker${error ? ' field--invalid' : ''}`}>
      <label htmlFor={id}>{label}{optional ? ' (необязательно)' : ''}</label>
      {value ? (
        <div className="participant-selected">
          <span><strong>{value.name || 'Участник'}</strong>{value.email ? <> — {value.email}</> : null}</span>
          {!disabled && <button type="button" className="button--secondary" onClick={() => onChange(null)}>Сменить</button>}
        </div>
      ) : (
        <div className="participant-search">
          <input id={id} value={query} disabled={disabled} autoComplete="off"
            placeholder="Начните вводить имя или email"
            aria-invalid={Boolean(error)}
            aria-describedby={error ? `${id}-error` : undefined}
            onFocus={() => setOpen(true)}
            onChange={event => { setQuery(event.target.value); setOpen(true); }} />
          {open && !disabled && (
            <div className="participant-options" role="listbox" aria-label={`Варианты: ${label}`}>
              {loading && <p className="muted small" role="status">Ищем…</p>}
              {failure && (
                <p className="small field-error" role="alert">
                  {failure.status === 404 ? 'Список участников недоступен.' : failure.message || 'Не удалось загрузить список участников.'}
                </p>
              )}
              {!loading && !failure && options.length === 0 && <p className="muted small">Ничего не найдено.</p>}
              {options.map(option => (
                <button key={`${option.id}`} type="button" role="option" aria-selected={false}
                  onClick={() => { onChange(option); setQuery(''); setOpen(false); }}>
                  {option.name}{option.email ? ` — ${option.email}` : ''}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
      {error && <p className="field-error" id={`${id}-error`}>{error}</p>}
    </div>
  );
}
