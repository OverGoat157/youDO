-- Optional fixture for an isolated demo database. Existing participants are never changed.
INSERT INTO users(name,email,role,rating,registered_at) VALUES
 ('Анна Смирнова','anna@mail.ru','CUSTOMER',4.80,CURRENT_DATE),
 ('Дмитрий Волков','dmitry@mail.ru','FREELANCER',4.90,CURRENT_DATE)
ON CONFLICT (email) DO NOTHING;
