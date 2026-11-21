# Backend заказов КР №3

Реализована часть Карима: JPA-модель заказа, общий сервис, REST, участники,
фильтры, версии, архив, заметки и пять массовых действий. В интеграцию включены
фронт Ангелины, исправления пользователей Юры и аудит Сергея (PR #23) и его страница (PR #24).
Миграции V1–V4 сохранены: модель использует существующие таблицы.

## Для Сергея: CSV и аналитика

Запись выполняется через Spring bean `OrderService`, а не через repository:

- `create(OrderDtos.Write)` — создание, `version=null`;
- `update(UUID, Write)` и `updateByCode(String code, Write)` — обновление с обязательной исходной `version`;
- `findByCode(code)` — `Optional<OrderDtos.Response>` с UUID и версией для снимка preview;
- `validateCreate(Write)`, `validateUpdate(UUID, Write)`, `validateUpdateByCode(code, Write)` — те же проверки без изменения сущности и без аудита;
- `findForExport(OrderFilter, sort)` — все совпавшие строки в заданном порядке, без ограничения страницей. `ExportRow` содержит code/title/description/categoryCode/status/budget/deadline/customerEmail/freelancerEmail/urgent/tags; связи разрешаются внутри транзакции.

Сервис сам проверяет EDITOR/ADMIN и запрет работы с временным паролем, включая
внутренние вызовы. Create/update используют обычный `@Transactional(REQUIRED)`:
вызовы из одного `@Transactional` apply присоединяются к его транзакции.
Ошибка любой строки должна выйти из apply и откатить весь файл вместе с аудитом.
Не ловить её внутри apply ради продолжения импорта. Bulk устроен иначе и для
CSV apply не используется.

Preview сохраняет исходные UUID/версии на сервере; при apply нужно передать
именно сохранённую версию, повторно вызвать сервис и в той же транзакции
пометить preview использованным. Нельзя подставлять свежую версию из БД вместо
версии снимка. Unique code и `@Version` защищают также гонку после проверки.
`VERSION_CONFLICT` возвращается с HTTP 409. Код заказа регистрозависим,
обрезаются только крайние пробелы. UPSERT архивного заказа запрещён; сервис
не восстанавливает его автоматически.

Email участников можно разрешать через `ParticipantRepository.findByEmailIgnoreCase`,
категорию — через `CategoryRepository.findByCode` (код категории uppercase).
В Write передаются внутренние числовые ID участников и UUID категории.
Сервис повторно проверяет роли участников и активность новой категории.
Для нового заказа разрешён входной статус (в том числе при переносе CSV),
но IN_PROGRESS/ON_REVIEW/COMPLETED всегда требуют исполнителя. При update
разрешены только обычные переходы статуса.

`OrderFilter.from(Map<String,String>)` и `OrderSpecifications.matching(filter,
priorityCalculator, today)` общие для списка и экспорта. Все условия выполняет
БД. Для аналитики доступны `OrderPriorityCalculator.today()` и
`expression(root, criteriaBuilder, today)` — SQL CASE для GROUP BY и фильтра.
Один `today` нужно получать один раз на всю сводку; priority в БД не сохраняется.

Приоритет из согласованного PLAN3:

| Условие (проверяется сверху вниз) | priority |
|---|---|
| COMPLETED или CANCELLED | LOW |
| urgent=true либо deadline < сегодня | HIGH |
| deadline от сегодня до сегодня + 3 дней включительно | MEDIUM |
| Остальные | LOW |

Сегодня определяется общим `Clock` в зоне `Europe/Moscow`, даже если сам Clock
имеет UTC. Архивность не меняет формулу; область активных в аналитике задаётся
отдельно через archived=false.

Страницы `/csv`, `/analytics`, `/audit` по распределению принадлежат Сергею.
Аудит уже подключён из PR #23/#24 и показывает события заказов. `/csv` и
`/analytics` пока показывают «Раздел пока недоступен». CSV endpoints и
AnalyticsService этим изменением не реализуются.

## JSON и маршруты

Создание `POST /api/orders` (201, Location):

```json
{
  "code":"WEB-001","title":"Сайт","description":"Описание",
  "categoryId":"00000000-0000-0000-0000-000000000001",
  "status":"OPEN","budget":"1000.00","deadline":"2027-01-31",
  "customerId":1,"freelancerId":null,"urgent":false,"tags":["java","веб"]
}
```

UUID категории и ID участников берутся из API, примерные ID не создаются
автоматически. `PUT /api/orders/{uuid}` принимает те же поля плюс `version`.
Заказчика менять нельзя. Служебные id/priority/archived/createdAt/updatedAt
во входе отклоняются. Ответ соответствует `frontend/src/features/orders/types.ts`:
публичный UUID, имена связей, бюджет строкой, даты ISO, теги, вычисленный priority,
архивность и версия.

`GET /api/orders`: q ищет title OR description OR code; остальные фильтры AND.
Поддержаны categoryId/status/urgent/priority/tag/archived=false|true|all,
budgetOp=eq|gt|gte|lt|lte + budgetValue; также budgetFrom/budgetTo включительно
для последней версии фронта. Диапазон и budgetOp одновременно запрещены.
Проценты/подчёркивания в q — обычные символы.
page=0, size=20 (максимум 100), sort=createdAt,desc по умолчанию.
Допустимые сортировки title/budget/deadline/createdAt, asc/desc; второй ключ id.
Страница: `{content,page,size,totalElements,totalPages}`.

`GET /api/participants?q=...&role=CUSTOMER|FREELANCER&page=0&size=20` ищет по
имени или email. Участники — прежние users из КР №1, отдельные от аккаунтов
входа. Для пустой базы их нужно перенести по `backend/migration.md`.

`DELETE /api/orders/{id}?version=...` архивирует (204).
`POST /api/orders/{id}/restore` принимает `{version}` и отдаёт карточку.
Повтор с текущей версией идемпотентен; устаревшая версия всегда 409.
`DELETE /api/orders/{id}/permanent?version=...` — только ADMIN при включённой
настройке и только для архивного заказа; удаляет теги/заметки, сохраняет аудит.

`POST /api/orders/bulk`: `{action,items:[{id,version}],status?,tag?}`,
до 100 уникальных UUID. ARCHIVE/RESTORE/CHANGE_STATUS/ADD_TAG/REMOVE_TAG.
Ответ `{succeeded:[{id,version}],failed:[{id,code,message}]}`.
Каждый элемент выполняется отдельной REQUIRES_NEW транзакцией через
TransactionTemplate, ошибка одного не отменяет другие.

`GET/POST /api/orders/{id}/notes`, `PUT/DELETE /api/orders/{id}/notes/{noteId}`:
запись `{text,pinned}`; автор и время серверные. Чужой orderId даёт 404.
Изменение заметки повышает версию заказа, поэтому параллельный архив не
позволяет записать заметку в архив. Фронт после изменения перечитывает карточку.
В аудит попадают UUID и код события, текст заметки не попадает.

Просроченные заказы читаются и архивируются. Обычное изменение требует
непрошедший дедлайн; теги разрешены без изменения остальных полей.
У COMPLETED/CANCELLED можно менять только теги, заметки и архив.

## Проверки

Изолированный стенд (рабочие контейнеры и public-схема приложения не меняются):

```sh
mkdir -p /tmp/youdo-orders-maven
MAVEN_CACHE_DIR=/tmp/youdo-orders-maven docker compose -p youdo-orders-verify \
  -f backend/compose.verify.yml run --rm verify
```

`OrdersIntegrationTest` использует отдельную orders_tests схему PostgreSQL;
миграции выполняются Flyway, Hibernate проверяет схему через validate.
Проверяются HTTP CRUD, роли, деньги/даты/участники/статусы, отключённая категория,
комбинированные фильтры, диапазон из фронта, priority, экспорт всех результатов,
preview без записи, updateByCode с исходной версией, архив/restore/permanent,
принадлежность заметок, partial bulk, откат CSV-транзакции вместе с аудитом
и две конкурентные транзакции: коммит проходит только у одной.
`OrderPriorityTest` проверяет границы и московскую полночь.

Проверено 22.11.2026: **45 backend-тестов, без ошибок и пропусков**, **38
frontend-тестов**, `npm run build` и полный `docker build -f backend/Dockerfile`.
Playwright на отдельном стенде подтвердил смену временного пароля и повторный
вход, создание заказа через форму, выбор участника, назначение исполнителя и
переход в IN_PROGRESS, заметку, архив/восстановление. Две вкладки одной версии:
первое изменение сохранено, второе получило 409 с сохранением введённого текста.
Страница аудита показывает реальное ORDER_CREATED и фильтруется по нему;
ошибок JavaScript в этом сценарии нет. Рабочий сервер на 8080 не перезапускался.
