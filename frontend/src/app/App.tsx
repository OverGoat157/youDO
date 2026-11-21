import { useEffect } from 'react';
import { linkProps, matchPath, navigate, useLocationPath } from './router';
import { isAdmin, session, useSession, type Account } from '../api/session';
import { apiFetch } from '../api/client';
import { LoadingScreen, ModulePlaceholder, NotFoundPage, Redirect } from './ui';
import { LoginPage } from '../features/auth/LoginPage';
import { ChangePasswordPage } from '../features/auth/ChangePasswordPage';
import { UsersPage } from '../features/users/UsersPage';
import { OrdersPage } from '../features/orders/OrdersPage';
import { OrderCardPage } from '../features/orders/OrderCardPage';
import { OrderFormPage } from '../features/orders/OrderFormPage';
import { CategoriesPage } from '../features/categories/CategoriesPage';
import './app.css';

interface NavItem {
  path: string;
  label: string;
  adminOnly?: boolean;
}

/**
 * После смены пароля сессия очищается и router уводит на вход.
 * Подменяем только clear: экран Юры не знает про уведомление, а пользователь должен
 * увидеть, что пароль изменён, а не «просто форму входа».
 */
const passwordChangeSession = {
  login: session.login,
  clear: () => session.clear('Пароль изменён. Войдите с новым паролем.'),
};

const NAV: NavItem[] = [
  { path: '/orders', label: 'Заказы' },
  { path: '/categories', label: 'Категории' },
  { path: '/csv', label: 'CSV' },
  { path: '/analytics', label: 'Аналитика' },
  { path: '/audit', label: 'Аудит', adminOnly: true },
  { path: '/admin/users', label: 'Пользователи', adminOnly: true },
];

export function App() {
  const location = useLocationPath();
  const state = useSession();

  useEffect(() => {
    if (state.status === 'restoring') void session.restore();
  }, [state.status]);

  const page = resolveRoute(location, state);
  const bare = location === '/login' || matchPath('/change-password', location) !== null;

  if (bare) {
    return (
      <>
        {state.status === 'anonymous' && state.notice && (
          <div className="alert app-notice" role="status">
            <p>{state.notice}</p>
          </div>
        )}
        {page}
      </>
    );
  }

  return (
    <div className="app-shell">
      <AppHeader user={state.user} location={location} />
      <div className="app-content">{page}</div>
    </div>
  );
}

function resolveRoute(location: string, state: ReturnType<typeof useSession>) {
  const path = location.split('?')[0];
  const isLogin = path === '/login';
  const isChangePassword = path === '/change-password';

  if (state.status === 'restoring') {
    return <LoadingScreen label="Проверяем сессию…" />;
  }

  if (isLogin) {
    if (state.status === 'authenticated' || state.status === 'password-change-required') {
      return <Redirect to={state.status === 'password-change-required' ? '/change-password' : '/orders'} />;
    }
    return <LoginPage session={session} navigate={navigate} />;
  }

  if (state.status === 'anonymous') {
    return <Redirect to="/login" />;
  }

  const user = state.user as Account;
  const mustChangePassword = state.status === 'password-change-required' || user.mustChangePassword;

  if (mustChangePassword) {
    return isChangePassword
      ? <ChangePasswordPage apiFetch={apiFetch} session={passwordChangeSession} navigate={navigate} required />
      : <Redirect to="/change-password" />;
  }

  if (isChangePassword) {
    return <ChangePasswordPage apiFetch={apiFetch} session={passwordChangeSession} navigate={navigate} />;
  }

  if (path === '/' || path === '') return <Redirect to="/orders" />;

  if (matchPath('/orders', path)) return <OrdersPage user={user} />;

  if (matchPath('/orders/new', path)) return <OrderFormPage user={user} />;

  const edit = matchPath('/orders/:id/edit', path);
  if (edit) return <OrderFormPage user={user} orderId={edit.id} key={edit.id} />;

  const card = matchPath('/orders/:id', path);
  if (card) return <OrderCardPage user={user} orderId={card.id} key={card.id} />;

  if (matchPath('/categories', path)) return <CategoriesPage user={user} />;

  if (matchPath('/admin/users', path)) {
    return isAdmin(user) ? <UsersPage apiFetch={apiFetch} currentUser={user} /> : <Redirect to="/orders" />;
  }

  if (matchPath('/csv', path)) {
    return (
      <ModulePlaceholder
        title="CSV: импорт и экспорт"
        owner="Сергей"
        description="Проверка файла, два режима применения и экспорт текущих условий подключаются из ветки feature/kr3-csv-analytics-audit как CsvPage."
      />
    );
  }

  if (matchPath('/analytics', path)) {
    return (
      <ModulePlaceholder
        title="Аналитика"
        owner="Сергей"
        description="Сводка по всей базе и группировки подключаются как AnalyticsPage; маршрут и меню уже готовы."
      />
    );
  }

  if (matchPath('/audit', path)) {
    if (!isAdmin(user)) return <Redirect to="/orders" />;
    return (
      <ModulePlaceholder
        title="Журнал аудита"
        owner="Сергей"
        description="Поиск, сортировка и страницы аудита подключаются как AuditPage; доступен только ADMIN."
      />
    );
  }

  return <NotFoundPage />;
}

function AppHeader({ user, location }: { user: Account | null; location: string }) {
  const items = NAV.filter(item => !item.adminOnly || isAdmin(user));
  return (
    <header className="app-header">
      <div className="app-header__inner">
        <a className="brand" {...linkProps('/orders')}>youDO</a>
        <nav aria-label="Основная навигация">
          <ul className="nav-list">
            {items.map(item => {
              const active = location === item.path || location.startsWith(`${item.path}/`);
              return (
                <li key={item.path}>
                  <a className={active ? 'nav-link nav-link--active' : 'nav-link'}
                    aria-current={active ? 'page' : undefined} {...linkProps(item.path)}>
                    {item.label}
                  </a>
                </li>
              );
            })}
          </ul>
        </nav>
        <div className="app-user">
          {user && <span className="muted small">{user.name} · {user.login}</span>}
          <button type="button" className="button--secondary"
            onClick={async () => { await session.logout(); navigate('/login', { replace: true }); }}>
            Выйти
          </button>
        </div>
      </div>
    </header>
  );
}
