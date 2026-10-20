# Демонстрационный проект преподавателя

Это отдельная часть КР №2. Наш youDO использует JavaFX → сервисы → JDBC → PostgreSQL из КР №1. JavaFX-клиент преподавателя использует REST API backend, который вместе с PostgreSQL запускается через Docker.

## Статус

На 21.10.2026 скачать архив преподавателя не удалось. Развёртывание его backend и запуск его клиента **ещё не проверены**. Для собственного проекта подготовлен [рабочий Docker-стенд](docker-local.md): PostgreSQL с seed, подключение JavaFX и контейнер полного прогона тестов. Он позволяет показать Docker и JDBC youDO; запуск оригинального REST-примера остаётся отдельной частью задания.

## Порядок после получения архива

Распакуйте весь архив в отдельную папку, сохранив вместе `backend/`, `javafx-client/`, `android-client/` и корневой `.env`. Не добавляйте `.env` с реквизитами в репозиторий youDO.

Из корня демонстрационного проекта:

```sh
docker compose --env-file .env -f backend/docker-compose.yml up -d --wait
docker compose --env-file .env -f backend/docker-compose.yml ps
curl --fail http://localhost:8080/
curl --fail http://localhost:8080/actuator/health
```

Откройте `http://localhost:8080/` и войдите реквизитами администратора из задания. Из `javafx-client/`, с JDK 21 и Maven:

```sh
mvn -Dstudenthub.api=http://127.0.0.1:8080/api javafx:run
```

Убедитесь, что вход работает, клиент показывает данные сервера и фильтры меняют результат. Запишите фактические контейнеры, ответ health и результат запуска клиента в этот документ после проверки.

Остановка из корня демонстрационного проекта:

```sh
docker compose --env-file .env -f backend/docker-compose.yml down
```

Не добавляйте `-v`: volume хранит данные PostgreSQL. Android-клиент в КР №2 запускать не требуется.
