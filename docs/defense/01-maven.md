# 1. Maven: от команды до класса приложения

[Оглавление](README.md) · [Дальше: FxApplication](02-fx-application.md)

## Что вводим

Из корня репозитория, когда PostgreSQL уже запущен:

```sh
mvn -Dyoudo.db.config=docker/db.properties javafx:run
```

Эта команда означает: Maven читает `pom.xml`, подставляет значение свойства `youdo.db.config` и выполняет цель `run` плагина JavaFX. В нашем `pom.xml` указан главный класс `ru.mirea.freelance.fx.FxApplication`. Плагин запускает JVM приложения; перед этим Maven подготавливает классы и ресурсы. Именно поэтому в консоли видно `resources`, `compile`, затем `javafx:run`.

`-Dyoudo.db.config=...` сначала является **свойством Maven**. В конфигурации JavaFX-плагина есть `<option>-Dyoudo.db.config=${youdo.db.config}</option>`. Оно передаёт тот же путь в запускаемую JVM как **системное свойство Java**. Затем `DatabaseManager` читает его через `System.getProperty("youdo.db.config")`. Если преподаватель спросит, почему одного `-D` недостаточно: Maven запускает приложение в отдельном процессе, и параметр требуется передать этому процессу.

## Зачем `pom.xml`

`pom.xml` сообщает Maven, как собирать проект. `groupId=ru.mirea`, `artifactId=youdo`, `version=1.0` идентифицируют артефакт. `packaging=jar` задаёт тип сборки. Получающийся JAR не содержит все зависимости, поэтому для обычного запуска интерфейса используется JavaFX Maven Plugin, а не старый `java -jar` из КР №1.

`maven.compiler.release=21` задаёт целевую версию Java. Код должен запускаться на JDK 21 или новее. Если классы скомпилированы для Java 21, а Maven запускает их на Java 17, появится `UnsupportedClassVersionError`: версия class file `65` соответствует Java 21, а Java 17 поддерживает только до `61`.

Ключевые зависимости:

- `javafx-controls`: кнопки, таблицы, вкладки и другие элементы JavaFX;
- `javafx-fxml`: загрузка XML-разметки интерфейса;
- `postgresql`: JDBC-драйвер для подключения к PostgreSQL;
- `poi-ooxml`: сохранённая функциональность XLSX из КР №1;
- `junit-jupiter`: тесты, а не работа приложения для пользователя.

Maven копирует файлы из `src/main/resources` в classpath сборки (`target/classes`). Поэтому `FxApplication` может обращаться к `/ru/mirea/freelance/fx/main.fxml` через `getResource`, а не прописывать абсолютный путь конкретного компьютера.

## Где смотреть код

- [pom.xml](../../pom.xml): `properties`, `dependencies`, `javafx-maven-plugin`.
- [FxApplication.java](../../src/main/java/ru/mirea/freelance/fx/FxApplication.java): главный класс интерфейса.
- [DatabaseManager.java](../../src/main/java/ru/mirea/freelance/util/DatabaseManager.java): чтение `youdo.db.config`.

`Main.java` — вход в сохранённое консольное приложение КР №1; для КР №2 JavaFX-плагин выбирает `FxApplication`.

## Проверка понимания

Ответь без подсказки:

1. Как `mvn javafx:run` узнаёт, какой Java-класс запускать?
2. Почему Maven должен работать под JDK 21?
3. Чем свойство Maven отличается от системного свойства JVM приложения в этой команде?
4. Почему FXML можно искать по пути, начинающемуся с `/`, хотя это не путь к файлу в корне диска?

<details>
<summary>Ответы для самопроверки</summary>

1. В `pom.xml` у JavaFX Maven Plugin указан `mainClass`.
2. Проект компилируется с `release=21`; JVM 17 не загрузит классы версии 65.
3. Свойство получает Maven, а `<option>` плагина передаёт его в отдельную JVM приложения.
4. `/` здесь означает корень classpath; Maven скопировал ресурсы из `src/main/resources`.

</details>
