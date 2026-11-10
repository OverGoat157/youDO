# Контракты инфраструктуры КР №3

Это реально предоставленные классы части Дениса. Они не означают готовность остальных модулей.

## Аккаунты — Юра

`security.AccountCredentialsStore` предоставляет findByLogin(normalizedLogin), findById(UUID), lock(UUID), changePassword(UUID, encodedPassword), bootstrap(login, name, encodedPassword). Их выполняет `JdbcAccountCredentialsStore` на тех же таблицах, которыми управляет `AccountService`. JDBC и JPA используют общую транзакцию и блокировку строки аккаунта. Отдавайте внутренний AccountCredentials со всеми полями; хеш не должен попадать в REST DTO. Логин хранится trim + lowercase(Locale.ROOT), уникальный email до 150 символов.

Модель будущего UserAccount: UUID id, name до 100, login до 150, passwordHash до 100, active, mustChangePassword, credentialsVersion, необязательный participantId BIGINT, createdAt/updatedAt UTC. Роли в account_roles: account_id/role, строки VIEWER/EDITOR/ADMIN. AccountCredentials.roles — назначенные роли; наследование прав выполняет security.

Авторизация и администрирование используют общую `PasswordPolicy`. BCrypt PasswordEncoder — общий bean cost=12; не заводите второй encoder с несовместимым форматом.

Все изменения ролей, сбросы пароля и отключения:

```java
@Transactional
public void updateAccount(UUID id, ...) {
    credentialsStore.lock(id); // Сначала аккаунт, потом сессии — единый порядок блокировок.
    // Проверить DTO, сохранить новые поля; повысить credentialsVersion.
    sessionRevoker.revokeAll(id);
    auditWriter.record("ACCOUNT_UPDATED", "ACCOUNT", id, "Учётная запись изменена");
}
```

changePassword в адаптере повышает credentialsVersion и снимает mustChangePassword. Административный reset должен установить BCrypt, mustChangePassword=true, повысить версию и отозвать все refresh. Не возвращайте temporaryPassword ни из одной операции. Явные поля временного пароля в DTO должны иметь безопасный toString. Определение active/credentialsVersion проверяется на каждом запросе, поэтому изменения действуют сразу.

## Заказы — Карим

Миграции V1–V4 определяют имена полей и ограничения. Order: внутренний BIGINT id, публичный UUID public_id. order_tags.order_id и order_notes.order_id — BIGINT FK, REST принимает UUID заказа. Account, category, note, audit — UUID. Категория в categories, прежнего orders.category после V2 нет. created_at/updated_at — timestamptz (Instant), version — BIGINT для @Version. Participants отображаются на прежнюю users; аккаунты входа отдельно.

`CurrentUser.require()`/`optional()` — доступ к principal. `AuditWriter.record(action,entityType,publicId,summary)` должен вызываться внутри транзакции сервиса после бизнес-проверок. Заказ и его аудит откатываются вместе. Для заметок author_id берётся из CurrentUser.id(), не из входного DTO. Признак PERMANENT_DELETE_ENABLED проверяется уже на HTTP-границе, но сервис также должен проверять права, архивное состояние и версию при внутренних вызовах из CSV/других сервисов.

Изменения схемы добавляет Денис следующей Flyway-версией. Применённые миграции сохраняются. Если модель требует других столбцов, согласуйте миграцию до подключения entity с ddl-auto=validate.

## CSV, аналитика и аудит — Сергей

AuditEvent — таблица audit_events: UUID id, actorId nullable, actorLogin nullable, action/entityType до 60, entityId nullable UUID, occurredAt UTC, summary до 500, correlationId до 64. События не зависят FK от удаляемых сущностей: история сохраняется. Невалидный логин не пишется в audit; LOGIN_FAILED имеет пустого actor. Для бизнес-события используйте стабильный код действия и короткое описание без паролей, токенов, содержимого CSV и персонального текста заметок.

AuditEventRepository принадлежит инфраструктуре; собственный query repository/service может читать AuditEvent через Criteria/Specification и возвращать DTO. Чтение журнала и бизнес-аналитика здесь не реализованы. Аудит успешного CSV должен участвовать в транзакции apply. Поле action в этом модуле: LOGIN, LOGIN_FAILED, LOGOUT, PASSWORD_CHANGED; владельцы добавляют свои коды согласованно.

## Web — Ангелина и Юра

Запросы auth описаны в README. При WEB обязательны Origin и credentials:include. Access — в памяти, cookie читает браузер. После 401 выполните один общий refresh с `{"clientType":"WEB"}` и повторите исходный запрос один раз; 403 не запускает refresh. При смене пароля ответ 204, нужно очистить access и предложить новый вход. Если refresh не удался, завершите сессию. Logout можно вызвать даже с истёкшим access, cookie очищается.

Ошибка:

```json
{"status":403,"code":"PASSWORD_CHANGE_REQUIRED","message":"Сначала смените временный пароль","fieldErrors":[],"correlationId":"...","timestamp":"2026-11-10T17:00:00Z"}
```

HttpOnly cookie в JavaScript не читается. WEB не получает refresh в JSON. Не пытайтесь использовать ANDROID как запасной браузерный путь: с Origin/cookie он отвергается. В fetch используйте ровно настроенный origin; localhost и 127.0.0.1 различаются. Для production с HTTPS включите secure-cookie и задайте фактический ALLOWED_ORIGINS, без `*`.

Docker ожидает frontend/package.json, package-lock.json, script build и результат dist/. До появления модуля используется backend/static/index.html. Здесь нет чужого router/apiFetch/auth-экрана.
