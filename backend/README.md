# youDO — backend КР №3

Самостоятельный Spring Boot 3.5.5 / Java 21 модуль. Модуль включает миграции, JWT-авторизацию, администрирование аккаунтов, справочник категорий и запись аудита. React-страницы входа, смены пароля и администрирования проверяются в [отдельном стенде](../docs/kr3-yura-integration.md). Общий React-клиент, API заказов, CSV, аналитика и чтение аудита ещё предстоит подключить.

## Запуск

Из корня youDO, нужны Docker Compose и Python 3:

```sh
python3 backend/scripts/setup_env.py
docker compose --env-file backend/.env -f backend/docker-compose.yml up -d --build --wait
docker compose --env-file backend/.env -f backend/docker-compose.yml ps
docker compose --env-file backend/.env -f backend/docker-compose.yml down
```

Генератор создаёт `.env` с правами 0600 и случайными секретами; существующий файл не меняет. Временный пароль `admin@youdo.local` смотрите в локальном `backend/.env`. Он не выводится скриптом. При первом входе требуется смена пароля. Перезапуск не меняет существующий аккаунт. После создания администратора переменную `BOOTSTRAP_ADMIN_PASSWORD` можно очистить. Не добавляйте `.env` и snapshot в Git.

Backend/web: `http://localhost:8080/`; health: `/actuator/health`; PostgreSQL: `127.0.0.1:55433`, база `youdo_backend`. Compose-проект `youdo-kr3` и volume `youdo-kr3_backend_data` отделены от `youdo-kr2`. `down` сохраняет данные. При изменении HTTP_PORT добавьте фактический origin в ALLOWED_ORIGINS. За reverse proxy настраивайте отдельное доверие к proxy: сейчас X-Forwarded-For игнорируется, лимит использует адрес непосредственного соединения.

Docker собирает `frontend/` через `npm ci && npm run build`, когда у Ангелины появятся package.json, lockfile и выходной каталог `dist/`; файлы попадают внутрь JAR. До подключения React доступна информационная страница. Runtime работает от UID 10001. В образе используется Java 21 из Maven-образа, поэтому для уменьшения размера позже можно заменить runtime на JRE с curl.

Для запуска без Docker нужны Java 21, Maven 3.9+ и отдельная PostgreSQL 16. Передайте DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET и при необходимости bootstrap-переменные в окружении, затем `mvn -f backend/pom.xml spring-boot:run`. Spring сам не читает `backend/.env`; Compose передаёт значения явно. Hibernate только проверяет схему (`validate`), SQL выполняет Flyway.

## Авторизация и API

Пять маршрутов: POST `/api/auth/login`, `/refresh`, `/logout`, `/change-password`, GET `/api/auth/me`. DTO и схема доступны в OpenAPI. Все POST требуют JSON, clientType обязателен при login/refresh/logout. Хеши и пароли никогда не входят в ответы. Ответ login/refresh: accessToken, tokenType=Bearer, expiresIn=900, user; Android дополнительно получает refreshToken.

Для WEB:

```json
{"login":"admin@youdo.local","password":"<из локального .env>","clientType":"WEB"}
```

Запрос приходит с Origin, точно совпадающим с ALLOWED_ORIGINS; fetch использует `credentials: 'include'`. Сервер выдаёт HttpOnly cookie `youdo_refresh`, Path=/api/auth, SameSite=Strict. В HTTPS установите COOKIE_SECURE=true. Refresh/logout отправляют `{"clientType":"WEB"}`, без refreshToken в теле. Access хранится только в памяти и отправляется как `Authorization: Bearer <token>`. Обработчики WEB проверяют Origin, включая login; отсутствие Origin отклоняется. JSON и точный список CORS дополняют защиту от CSRF. Наличие cookie или Origin запрещает переход на ANDROID-контракт. Все остальные изменения требуют явный Bearer и не используют cookie как авторизацию.

ANDROID (также подходит для проверки через curl без браузера): login с clientType=ANDROID, без Origin/cookie; refresh/logout — `{"clientType":"ANDROID","refreshToken":"..."}`. Сессия привязана к clientType, сменить транспорт существующего токена нельзя.

Смена пароля: `{"currentPassword":"...","newPassword":"..."}` с Bearer, для WEB также Origin. Политика: 12–72 **байта UTF-8**, верхний/нижний регистр, цифра, спецсимвол; BCrypt cost=12. После смены пароля все refresh отозваны, credentialsVersion увеличена, нужен новый вход. До смены временного пароля разрешены только me/change-password/logout; refresh и бизнес-API возвращают PASSWORD_CHANGE_REQUIRED.

Access живёт 15 минут, сервер проверяет active, credentialsVersion и актуальные роли аккаунта при каждом запросе. После logout уже выданный access действует до истечения; refresh отозван. Изменение версии при смене/сбросе пароля, ролей и отключении сразу делает старый access недействительным. Refresh живёт 14 дней **от входа**, ротация не продлевает срок сессии. В БД только SHA-256 refresh; использованный токен не принимается снова. Конкурентные refresh сериализуются блокировкой PostgreSQL, проходит один запрос. Frontend должен иметь один refresh на все одновременные 401.

После пяти неверных входов пара нормализованный login/IP блокируется на 15 минут, следующие попытки — 429. Счётчик хранится в PostgreSQL и переживает перезапуск. Успех сбрасывает его. Ответ на неизвестный логин и неверный пароль одинаковый. X-Forwarded-For от клиента лимит не обходит.

VIEWER читает; EDITOR включает просмотр и запись заказов/CSV/заметок; ADMIN включает EDITOR и администрирование. Категории изменяет ADMIN. Permanent delete закрыт всем, пока PERMANENT_DELETE_ENABLED=false; затем только ADMIN. Проверка архивного состояния/версии — в сервисе заказов Карима. Swagger `/swagger-ui.html` и `/v3/api-docs` защищены ADMIN и требуют Bearer. Для интерактивного Swagger понадобится клиент/локальный proxy, который добавляет Authorization к запросу HTML и его ресурсов; обычный переход по ссылке без заголовка вернёт 401. OpenAPI JSON можно получить обычным HTTP-клиентом с Bearer.

## Контракты для команды

Подробности и примеры: [contracts.md](contracts.md). `AccountCredentialsStore` читает снимки аккаунтов и блокирует строки для операций JWT через JDBC. `AccountService` администрирует те же аккаунты через JPA. Оба используют одну политику паролей, BCrypt и отзыв сессий. Маршруты администрирования и категорий описаны в [контракте REST](../docs/kr3-yura-integration.md#rest-для-ангелины-и-карима).

`CurrentUser.require()` отдаёт UUID, имя, логин, роли, mustChangePassword и версию без хеша. `SessionRevoker.revokeAll(accountId)` отзывает сессии. `AuditWriter.record(action, entityType, entityId, safeSummary)` участвует в **существующей транзакции** изменения; неуспешные входы пишутся отдельной транзакцией. Корреляция: X-Correlation-ID, допустимы 1–64 ASCII буквы/цифры/дефис/подчёркивание, иначе сервер создаёт UUID. ID возвращается в заголовке, ошибке и аудите. Единые ошибки: status/code/message/fieldErrors/correlationId/timestamp, без SQL и stack trace.

## Миграции и перенос КР №1

V1 — исходные users/orders без DROP; V2 — категории, UUID, code, версии/архив, теги и индексы, backfill непустой базы; V3 — аккаунты, роли, refresh, аудит, лимит входа и заметки. V4 добавляет версии аккаунтам и категориям и расширяет код категории до 50 символов. Новые изменения схемы добавляются отдельными миграциями V5+.

Подробная процедура: [migration.md](migration.md). Снимок читается в REPEATABLE READ READ ONLY, импорт — одной транзакцией в пустые users/orders серверной БД. Сохраняются внутренние BIGINT, связи, деньги, даты, nullable исполнитель. UUID новые, code=LEGACY-id, категории связываются по прежнему enum. Повторный перенос поверх данных отклоняется, sequences восстанавливаются.

## Проверки

Без локальной Java, отдельная PostgreSQL в tmpfs:

```sh
mkdir -p /tmp/youdo-kr3-maven
docker compose -f backend/compose.verify.yml run --rm verify
docker compose -f backend/compose.verify.yml down
```

При другом UID/GID задайте LOCAL_UID/LOCAL_GID; MAVEN_CACHE_DIR задаёт каталог кеша Maven. Тесты выполняют миграции и HTTP/security-сценарии на настоящей PostgreSQL. Для локального `mvn -f backend/pom.xml test` задайте TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD, указывающие на **отдельную тестовую** БД. Используются отдельные схемы auth_tests и migration_*, рабочая схема public не очищается.

Протокол выполненных проверок и текущие ограничения: [verification.md](verification.md). Материалы к защите: [defense.md](defense.md).

Первичные справочники для решений по инфраструктуре: [Spring Security — CSRF и cookie-аутентификация](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html), [Spring Boot — Flyway и инициализация БД](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html). Конкретный HTTP-контракт и выполненные проверки описаны в этом модуле.
