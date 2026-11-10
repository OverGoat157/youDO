# Проверки части Дениса — 10.11.2026

Ветка `feature/kr3-infrastructure-auth`, основа `main` da6757e. Проверки ниже выполнены, а не только запланированы.

## Автотесты

```sh
mkdir -p /tmp/youdo-kr3-maven
docker compose -f backend/compose.verify.yml run --rm verify
```

Результат: **17 tests, failures 0, errors 0, skipped 0; BUILD SUCCESS**. Java 21 / Maven 3.9.9 в контейнере, PostgreSQL 16 в отдельном `youdo-kr3-verify`, данные тестов в tmpfs. Рабочая база не используется. Локальные Java 17 / Maven 3.6.3 не использовались для сборки этого модуля.

AuthIntegrationTest — 16 сценариев: Spring Context/Flyway, login/me и отсутствие секретов в ответе, correlation ID и hash refresh, наследование VIEWER/EDITOR/ADMIN и прямой HTTP, запрет permanent delete по умолчанию, временный пароль и повторный вход после смены, refresh/rotation/logout, два конкурентных refresh (один 200 и один 401), истечение access/refresh, смена ролей/версии/active, лимит пяти ошибок и окончание окна, сброс счётчика успешным входом/игнорирование X-Forwarded-For, cookie/Origin и запрет обхода через ANDROID, неизменяемый clientType сессии, единые ошибки 400/401/403/404, UTF-8-политика пароля, откат аудита вместе с транзакцией, bootstrap без сброса прежнего пароля. Несколько перечисленных проверок объединены в один тестовый метод.

MigrationIntegrationTest — V1, затем заполненные users/orders с явными ID, nullable исполнителем, кириллицей, суммами и датами, затем V2/V3. Проверены сохранность, UUID/code, категории, checksum Flyway и повторный migrate без новых изменений.

## Итоговый Docker и HTTP

```sh
python3 backend/scripts/setup_env.py
docker compose --env-file backend/.env -f backend/docker-compose.yml up -d --build --wait
```

Образ собран, PostgreSQL и backend healthy. Runtime работает от UID 10001, корневой Compose/JavaFX не менялись. Проверены реальными HTTP-запросами:

| Сценарий | Результат |
|---|---|
| `/` и `/actuator/health` | 200, health UP |
| Вход временного тестового ADMIN | 200, mustChangePassword=true |
| OpenAPI до смены пароля | 403 |
| Смена пароля | 204 |
| Старый access и старый refresh после смены | 401 |
| Новый вход и me | 200 |
| OpenAPI JSON и Swagger HTML с Bearer ADMIN | 200 |
| Refresh / повтор старого refresh | 200 / 401 |
| Logout / refresh после него | 204 / 401 |
| Несуществующий API с действующим access | 404, NOT_FOUND |
| WEB login без Origin | 403 |

Использовался отдельный временный аккаунт, после проверки он удалён, его refresh удалены каскадно. Bootstrap-admin не менялся, его временный пароль остаётся в локальном backend/.env. Пароли/токены в протокол не выводились. Проверенный JAR контейнера побайтно совпал с target/classes для всех классов и ресурсов.

## Перенос реальной копии КР №2

```sh
python3 backend/scripts/transfer_legacy.py snapshot \
  --source-container youdo-kr2-db-1 --out .local/kr3/legacy.snapshot.json
python3 backend/scripts/transfer_legacy.py import \
  --target-container youdo-kr3-db-1 --snapshot .local/kr3/legacy.snapshot.json
```

Снимок содержит **6 пользователей и 12 заказов**, сумма бюджетов **441000.00**. В новой БД 12 уникальных public_id, code уникальны. Сравнены все старые поля участников и заказов: ID, title/description, role/rating/email, category, budget, даты, customer/freelancer, включая NULL. Совпадение полное; исходная БД не изменилась. Sequences не отстают от перенесённых ID.

Повторный import в непустые users/orders вернул отказ и exit=1 без изменения данных. После `down` и `up -d --wait` без удаления volume оба сервиса снова healthy, все поля 6 пользователей/12 заказов повторно совпали со снимком, sequences и уникальность UUID/code сохранены. Исходная БД также повторно проверена и не изменилась.

Локальные диагностические сценарии `.local/kr3/smoke.py` и `.local/kr3/check_transfer.py` остаются вне Git, вместе со снимком. Для команды воспроизводимый автоматический набор находится в src/test; перенос описан в migration.md.

## Границы результата

- Это законченная инфраструктурная часть, а не вся КР №3. Бизнес-API заказов, административные API аккаунтов/категорий, CSV/аналитика/просмотр аудита и React ещё должны прийти из веток владельцев.
- AccountCredentialsStore сейчас работает через настоящий JDBC-адаптер. Юра подключает AccountService к этому порту и свою NewPasswordPolicy; контроллер администрирования здесь не дублируется.
- Главная страница — временная информационная страница. Ветка сборки настоящего React поддержана Dockerfile, но не проверена на отсутствующем frontend.
- Swagger защищён Bearer ADMIN. HTTP с заголовком проверен; обычная навигация браузера сама Authorization не добавляет. Потребуется интеграция с авторизованным клиентом/локальным proxy для интерактивной браузерной демонстрации.
- Через agent-desktop браузерная проверка не выполнена: текущий адаптер вернул PLATFORM_NOT_SUPPORTED, display_unavailable=not_configured. Другой инструмент управления интерфейсом не использовался.
- Архив примера преподавателя не получен; его запуск не заявляется. Полная командная приёмка из PLAN3 проводится после подключения остальных модулей.
