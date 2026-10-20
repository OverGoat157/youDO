-- Только для отдельной демонстрационной БД: скрипт удаляет прежние данные.
-- Дедлайны относительно дня загрузки: есть будущие и просроченные заказы.
TRUNCATE orders, users RESTART IDENTITY;

INSERT INTO users (name, email, role, rating, registered_at) VALUES
    ('Анна Смирнова',   'anna@mail.ru',    'CUSTOMER',   4.80, '2026-01-15'),
    ('Игорь Петров',    'igor@mail.ru',    'CUSTOMER',   4.20, '2026-02-03'),
    ('Мария Ковалёва',  'maria@mail.ru',   'CUSTOMER',   3.90, '2026-03-10'),
    ('Дмитрий Волков',  'dmitry@mail.ru',  'FREELANCER', 4.90, '2026-01-20'),
    ('Елена Соколова',  'elena@mail.ru',   'FREELANCER', 4.50, '2026-02-14'),
    ('Артём Лебедев',   'artem@mail.ru',   'FREELANCER', 3.70, '2026-04-01');

-- customer_id 1..3 — заказчики, freelancer_id 4..6 — фрилансеры
INSERT INTO orders (title, description, category, budget, deadline, status, customer_id, freelancer_id, created_at) VALUES
    ('Лендинг для кофейни',                'Одностраничный сайт с меню и картой',            'DEVELOPMENT',  45000.00, CURRENT_DATE + 14, 'OPEN',        1, NULL, CURRENT_TIMESTAMP - INTERVAL '60 days'),
    ('Логотип для мобильного приложения',  'Три варианта, исходники в векторе',               'DESIGN',       15000.00, CURRENT_DATE + 7, 'OPEN',        2, NULL, CURRENT_TIMESTAMP - INTERVAL '61 days'),
    ('Тексты для карточек товаров',        '50 карточек по 300 символов',                     'COPYWRITING',   8000.00, CURRENT_DATE - 3, 'OPEN',        3, NULL, CURRENT_TIMESTAMP - INTERVAL '62 days'),
    ('Интернет-магазин на Vue',            'Каталог, корзина, оплата',                        'DEVELOPMENT', 150000.00, CURRENT_DATE + 30, 'IN_PROGRESS', 1, 4,    CURRENT_TIMESTAMP - INTERVAL '63 days'),
    ('Настройка рекламы в Яндекс Директ',  'Кампания на 20 ключевых запросов',                'MARKETING',    25000.00, CURRENT_DATE - 5, 'IN_PROGRESS', 2, 5,    CURRENT_TIMESTAMP - INTERVAL '64 days'),
    ('Дизайн презентации для инвесторов',  '15 слайдов в фирменном стиле',                    'DESIGN',       12000.00, CURRENT_DATE + 10, 'IN_PROGRESS', 3, 6,    CURRENT_TIMESTAMP - INTERVAL '65 days'),
    ('Telegram-бот для записи клиентов',   'Запись на услуги, напоминания',                   'DEVELOPMENT',  60000.00, CURRENT_DATE + 5, 'ON_REVIEW',   2, 4,    CURRENT_TIMESTAMP - INTERVAL '66 days'),
    ('Статьи для блога о кофе',            '10 статей по 5000 символов',                      'COPYWRITING',  18000.00, CURRENT_DATE - 1, 'ON_REVIEW',   1, 5,    CURRENT_TIMESTAMP - INTERVAL '67 days'),
    ('Фирменный стиль для пекарни',        'Логотип, цвета, шрифты, визитки',                 'DESIGN',       40000.00, CURRENT_DATE - 30, 'COMPLETED',   3, 6,    CURRENT_TIMESTAMP - INTERVAL '68 days'),
    ('Парсер цен конкурентов',             'Сбор цен с трёх сайтов раз в сутки',              'DEVELOPMENT',  30000.00, CURRENT_DATE - 45, 'COMPLETED',   1, 4,    CURRENT_TIMESTAMP - INTERVAL '69 days'),
    ('SMM-стратегия на квартал',           'Контент-план и рубрикатор для трёх соцсетей',     'MARKETING',    35000.00, CURRENT_DATE - 20, 'COMPLETED',   2, 5,    CURRENT_TIMESTAMP - INTERVAL '70 days'),
    ('Перевод сайта на английский',        'Около 20 страниц',                                'OTHER',         3000.00, CURRENT_DATE - 10, 'CANCELLED',   3, NULL, CURRENT_TIMESTAMP - INTERVAL '71 days');
