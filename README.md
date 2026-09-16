# youDO — биржа фриланса

Консольная информационная система «Биржа фриланса» для дисциплины «Программирование корпоративных систем» (КР 1).
Группа ЭФБО-17-24: Джумагулов Карим, Епифанов Денис, Гришутина Ангелина, Васин Сергей.

Заказчики публикуют заказы на фриланс-работу, фрилансеры их выполняют. Система ведёт пользователей и заказы, проверяет бизнес-правила, ищет, фильтрует, сортирует, считает статистику и экспортирует данные в Excel/CSV.

## Стек

- Java 17, Maven
- PostgreSQL 16, JDBC (`PreparedStatement`, try-with-resources)
- Apache POI — экспорт `.xlsx`
- JUnit 5 — тесты бизнес-правил

## Архитектура

```
Console UI (ui/)  →  Service (service/)  →  Repository / JDBC (repository/)  →  PostgreSQL
```

| Слой | Что делает |
|---|---|---|
| UI | меню, безопасный ввод, вывод таблиц; никакого SQL и бизнес-логики |
| Service | бизнес-правила, поиск/фильтрация/сортировка (Stream API), статистика |
| Repository | все SQL-запросы, объекты |
| Model | User, Order, enum UserRole, OrderStatus, OrderCategory |
| Exceptions | BusinessException, ValidationException, EntityNotFoundException, DatabaseException |
| Util | DatabaseManager, ExcelExporter, CsvExporter, DbInspector |

## Предметная модель

- **User** — участник биржи: `id, name, email (unique), role (CUSTOMER | FREELANCER), rating, registeredAt`
- **Order** — заказ на фриланс-работу: `id, title, description, category, budget, deadline, status, customerId → users, freelancerId → users (nullable), createdAt`
- **OrderStatus**: `OPEN → IN_PROGRESS → ON_REVIEW → COMPLETED`, из `OPEN`/`IN_PROGRESS` возможен `CANCELLED`

### Бизнес-правила

1. Название заказа не может быть пустым.
2. Бюджет заказа больше нуля.
3. Дедлайн не может быть в прошлом.
4. Заказчик должен существовать и иметь роль `CUSTOMER`, исполнитель — роль `FREELANCER`.
5. Заказчик не может быть исполнителем своего заказа.
6. Разрешены только переходы статусов по схеме выше; в работу можно взять только заказ с назначенным исполнителем.
7. Нельзя удалить пользователя, у которого есть активные заказы.
8. Email пользователя уникален.

## Меню

```
============ БИРЖА ФРИЛАНСА youDO ============
1. Пользователи
2. Заказы
3. Поиск заказов
4. Фильтрация заказов
5. Сортировка заказов
6. Статистика
7. Экспорт данных
8. Вывести таблицы базы данных
0. Выход
```