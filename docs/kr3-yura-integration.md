# КР №3: часть Юры — аккаунты, категории и пользовательские страницы

Аккаунты и категории работают в общем Spring Boot-модуле. Сборка React-страниц и их тесты запускаются из `frontend/verification`.

## Проверка

Backend проверяется вместе с авторизацией и миграциями по [инструкции запуска тестов](../backend/README.md#проверки). Тесты административных контроллеров используют H2, а тест JWT и фактического отзыва сессий работает с PostgreSQL.

```sh
cd frontend/verification
npm ci
npm test
npm run build
```

Стенд проверяет компоненты React с подставленным HTTP-транспортом. Компоненты готовы к подключению в общий router и session store.

## Аккаунты и безопасность

- JDBC-адаптер авторизации получает пароль, роли и версию credentials из таблиц аккаунтов. JPA используется в `AccountService` для административных операций. Хеш исключён из ответов.
- `PasswordPolicy` используется для временных и новых паролей. Учитываются 12–72 байта UTF-8, верхний и нижний регистр, цифра и спецсимвол.
- Смена и сброс пароля, отключение, изменение ролей и изменение логина увеличивают `credentialsVersion` и отзывают refresh-сессии. Администрирование и категории используют общий журнал аудита.
- Ошибки аккаунтов и категорий возвращает `ApiExceptionHandler`. Идентификатор запроса совпадает в заголовке `X-Correlation-ID`, теле ошибки и записи аудита.
- V4 добавляет `version` в `user_accounts` и `categories` и расширяет `categories.code` до 50 символов. Уникальность логина, роли и кода категории обеспечивается схемой PostgreSQL.

## REST для Ангелины и Карима

Аккаунт в ответах: `{id,name,login,roles,active,mustChangePassword,credentialsVersion}`.
Ни хеша, ни временного пароля в ответах нет.

| Запрос | Body / результат |
|---|---|
| GET /api/admin/users?page=0&size=20 | `{content,page,size,totalElements,totalPages}`, сортировка login,id, size 1–100, page >= 0 |
| POST /api/admin/users | `{name,login,roles,temporaryPassword}` → 201, Location, DTO; active=true, mustChangePassword=true |
| PUT /api/admin/users/{id} | `{name,login,roles,active}` → DTO; собственный неизменённый login разрешён |
| POST /api/admin/users/{id}/reset-password | `{temporaryPassword}` → DTO, mustChangePassword=true |
| GET /api/categories | Все категории, включая отключённые, порядок name,id |
| GET /api/categories?active=true | Только активные; active=false — только отключённые |
| POST /api/categories | `{code,name,active}` → 201, Location, `{id,code,name,active}` |
| PUT /api/categories/{id} | `{code,name,active}` → DTO |

Категории не удаляются; код нормализуется в uppercase, допустимы латинские буквы,
цифры и `_`, начало с буквы. Карим вызывает
`CategoryService.requireAssignable(requestedId, previousId)`: previousId=null
для нового заказа. Отключённую категорию можно сохранить без замены только если
она уже назначена этому заказу; previousId берётся из БД, не из клиентского body.

## Ангелине: подключение страниц

Экспорты: `LoginPage`, `ChangePasswordPage`, `UsersPage` (именованные).
Все props/типы находятся в `features/auth/contracts.ts` и в файлах страниц.

- `apiFetch<T>(path, RequestInit?)` возвращает **распарсенный JSON**, для 204 —
  undefined. Ошибки: `{status, code, message, fieldErrors:[{field,message}], correlationId}`.
  Поддерживает AbortSignal. Ссылка на функцию стабильна между рендерами.
- `session.login({login,password,clientType:'WEB'})` выполняет общий запрос входа,
  сохраняет access/user **в памяти** и возвращает `{user}`.
- `session.clear()` очищает session/access; `navigate(path)` — адаптер router.
- LoginPage получает session + navigate; маршрутизирует на `/change-password`
  при mustChangePassword и `/orders` иначе.
- ChangePasswordPage получает apiFetch + session + navigate + required.
  Router должен оставлять сообщение об успешной смене видимым после clear либо
  показывать аналогичное сообщение на login. Общая защита маршрутов учитывает
  mustChangePassword при загрузке/refresh, а не только после login.
- UsersPage получает apiFetch + currentUser. Маршрут `/admin/users` только ADMIN.
  Создание/reset генерируют временный пароль через Web Crypto; после закрытия
  формы компонент размонтирован, пароль не пишется в localStorage/логи.
- Подключить `src/styles/global.css` один раз в app entry. Общие классы:
  page, page-header, card, actions, pagination, table-scroll, alert/alert--error,
  button--secondary/button--danger, badge, stats-grid, stat-card, stat-value.
  Варианты badge: active/inactive/warning, open/in-progress/on-review/completed/
  cancelled, low/medium/high/overdue. Статус всегда сопровождается текстом.

## После подключения общего React-клиента

Проверьте восстановление сессии при обновлении страницы, ограничения маршрутов по ролям, возврат к форме входа после смены пароля и работу пользовательских страниц с действующим API.
