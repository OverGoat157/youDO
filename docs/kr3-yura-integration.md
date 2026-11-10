# КР №3: часть Юры — аккаунты, категории и пользовательские страницы

Ветка: `feature/kr3-users-references`, от `origin/main` (`da6757e`).
В исходном main нет backend/frontend КР №3. Эта ветка содержит модуль Юры;
общие сборки, JWT, Flyway, router и API/session transport остаются у владельцев
по PLAN3 из архива задания. JavaFX КР №2 не изменён.

## Проверка до объединения веток

```sh
mvn -f backend/verification/pom.xml test
cd frontend/verification
npm ci
npm test
npm run build
```

`backend/verification/pom.xml` — отдельный стенд с зависимостями Spring Boot
3.5.5 и Java 21 из ТЗ. Он компилирует реальные исходники из `backend/src`,
проверяет HTTP через MockMvc, настоящий JPA/H2, BCrypt и method security.
H2 создаёт только тестовую БД в памяти. Адаптеры аудита и отзыва в тестах — mocks.
JWT, refresh, PostgreSQL/Flyway и Docker этим стендом не проверяются.

`frontend/verification` — отдельный стенд, не замена общему package.json Ангелины.
Сборка проверяет TypeScript и выпускает библиотеку страниц с CSS. Тесты проверяют
реальные компоненты React в jsdom с подставленным transport. Полного приложения
и собственного refresh-клиента здесь нет.

После интеграции общая backend-сборка подхватит `src/main/java` и `src/test/java`.
В тестовом приложении задан явный список пакетов, чтобы не поднимать JWT/Flyway.
Не включать `verification` в production Docker context как отдельное приложение.

## Денису: аккаунты и безопасность

`AccountService`:

- `findByLogin(login)` нормализует `strip + lowercase(Locale.ROOT)`;
  `findById(UUID)` возвращает внутренний `UserAccount`, отсутствующий ID даёт 404.
- login — email, отдельного независимого поля email нет. Имя до 100, login до 150.
- `matchesPassword(account, password)` проверяет BCrypt и active. Хеш доступен
  только внутреннему коду; ни DTO, ни JSON entity его не сериализуют.
- `createBootstrapAdmin(name, login, password)` создаёт ADMIN с временным паролем;
  существующую запись не меняет. Параметры runner получает из env, не из SQL.
- `changePassword(authenticatedId, currentPassword, newPassword)` проверяет текущий
  пароль, единую политику и отличие нового, снимает mustChangePassword, повышает
  credentialsVersion, отзывает сессии. ID взять из CurrentUser, не из body.
- `PasswordPolicy.validate(password)` — один контракт для auth и admin:
  12–72 **байта UTF-8**, верхний/нижний регистр, цифра, спецсимвол;
  NUL и некорректные суррогаты запрещены. Пароль не trim-ится.
- Изменение роли, отключение, reset, change-password и изменение login повышают
  credentialsVersion. При сохранении одного имени версия credentials не меняется.
  Изменения аккаунта сериализованы pessimistic lock, дополнительно есть @Version.

Обязательные порты `AccountIntegrations.SessionRevoker` и
`AccountIntegrations.AuditWriter` объявлены внутри модуля Юры, чтобы не создавать
конкурирующие файлы инфраструктуры. Подключить два Spring bean-адаптера:

```java
@Bean
AccountIntegrations.SessionRevoker accountSessionRevoker(SessionRevoker shared) {
    return shared::revokeAll;
}
@Bean
AccountIntegrations.AuditWriter accountAuditWriter(AuditWriter shared) {
    return shared::record;
}
```

`shared` — реальные сервисы Дениса, не эти вложенные интерфейсы. Вызовы обязаны
участвовать в той же транзакции, что изменение аккаунта/категории. Нет fallback
no-op: при отсутствии адаптеров запуск должен завершаться ошибкой внедрения.
Аудит содержит только статичные описания операций, UUID и тип события, без пароля.

В общей security-конфигурации обязательно `@EnableMethodSecurity` и authentication
на `/api/**`. Контроллеры и сервисы admin защищены `hasRole('ADMIN')`; категории
читаются авторизованными. Authorities: `ROLE_VIEWER`, `ROLE_EDITOR`, `ROLE_ADMIN`.
Фильтр Дениса проверяет active и credentialsVersion каждого access JWT и разрешает
mustChangePassword только `/me`, `/change-password`, `/logout`.
После смены пароля AuthController очищает refresh cookie и возвращает 204;
web очищает access/session и предлагает повторный вход.

`AccountProblem` содержит status/code/message/field без секретных значений.
Для auth-endpoint общий обработчик Дениса должен переводить его в общий JSON.
`AccountExceptionHandler` ограничен двумя контроллерами Юры, обрабатывает 400,
404, 409, не выводит rejectedValue/SQL/exception text. Общий correlation-фильтр
кладёт проверенный ID в request attribute `correlationId`; при его отсутствии
модуль генерирует UUID и возвращает его в JSON и `X-Correlation-ID`.
401/403 и `PASSWORD_CHANGE_REQUIRED` — общий security handler Дениса.

### Схема для Flyway (SQL выпускает Денис)

| Таблица | Поля / ограничения |
|---|---|
| user_accounts | id UUID PK; name varchar(100) NOT NULL; login varchar(150) NOT NULL UNIQUE (`uk_account_login`); password_hash varchar(60) NOT NULL; active boolean NOT NULL; must_change_password boolean NOT NULL; credentials_version bigint NOT NULL DEFAULT 0; version bigint NOT NULL DEFAULT 0 |
| account_roles | account_id UUID NOT NULL FK user_accounts(id); role varchar(20) NOT NULL; UNIQUE(account_id, role) (`uk_account_role`); CHECK role IN ('VIEWER','EDITOR','ADMIN') |
| categories | id UUID PK; code varchar(50) NOT NULL UNIQUE (`uk_category_code`); name varchar(100) NOT NULL; active boolean NOT NULL; version bigint NOT NULL DEFAULT 0 |

Добавить CHECK login = lower(trim(login)), CHECK credentials_version >= 0,
CHECK version >= 0 и CHECK code = upper(code). Рекомендуемый индекс списка
аккаунтов `(login, id)`, категорий `(active, name, id)`. UUID генерирует приложение.
Пароли не добавлять в миграции. Старую `users` не менять для авторизации.
Связи orders.category_id и backfill прежнего enum принадлежат миграциям Дениса.

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

В этой ветке нет отправки сообщений команде. Документ и исходники — материал
для ревью Дениса (backend) и Ангелины (web), а не утверждение, что согласование
или совместный прогон уже состоялись.

## Проверка после интеграции

1. Применить Flyway на пустой и перенесённой PostgreSQL, проверить validate,
   UNIQUE при конкурентных запросах и сохранение ссылок старых заказов.
2. Войти bootstrap-admin, проверить ограничение mustChangePassword, сменить
   пароль, войти заново. Проверить 401 старого access и отказ старого refresh.
3. Создать VIEWER и EDITOR; напрямую проверить 403 на все admin/category mutations.
4. Изменить роли, отключить аккаунт, сбросить пароль; проверить отзыв access/refresh,
   аудит и отсутствие секретов в HTTP/логах.
5. Проверить общую React-сборку, router и refresh; клавиатуру, фокус, ошибки,
   сохранение введённых данных, ширину 360px и увеличение масштаба до 200%.
6. Запустить полный Compose и совместно пройти сценарии с Денисом/Ангелиной.

## Выполненные проверки — 10 ноября 2026

- Backend: 10 тестов, 0 ошибок. Проверены границы UTF-8/BCrypt, обязательные
  классы символов, HTTP 401/403 для ролей, создание/редактирование/reset,
  версия credentials и вызов отзыва, смена пароля, запрет входа отключённому
  аккаунту, пагинация, уникальность login, категории и сохранение отключённой
  ссылки, отсутствие секретов в JSON/DTO, идемпотентный bootstrap.
- Frontend: 7 тестов, 0 ошибок. Генерация пароля, вход и mustChangePassword,
  ответ 429, подтверждение нового пароля, очистка сессии, ограничение admin UI,
  серверная пагинация, подтверждение reset, удаление временного пароля после
  закрытия формы, сохранение ввода при ошибке.
- TypeScript typecheck и Vite library build: успешно.
- Полный браузерный прогон, PostgreSQL, JWT/refresh, общий router и Compose
  пока не проверены: нужны изменения других участников, которых нет в main.
