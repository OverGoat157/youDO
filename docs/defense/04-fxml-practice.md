# 4. FXML, контроллеры и код по памяти

[Назад: AppContext](03-app-context.md) · [Оглавление](README.md) · [Большой конспект](../defense-guide.md)

Открой [main.fxml](../../src/main/resources/ru/mirea/freelance/fx/main.fxml) и [MainController.java](../../src/main/java/ru/mirea/freelance/fx/controller/MainController.java).

## Что находится в `main.fxml`

Корень — `TabPane` с `fx:id="tabs"`. Атрибут `fx:controller` указывает `MainController`. Внутри три вкладки: заказы, пользователи и статистика. Их содержимое подключается через `fx:include` из `orders.fxml`, `users.fxml`, `statistics.fxml`.

FXML — XML-описание интерфейса. Оно отвечает на вопросы «какие элементы есть» и «как они вложены». Контроллер отвечает на вопросы «что произойдёт при действии» и «какие данные показать». Например, `fx:id="tabs"` связывается с `@FXML private TabPane tabs`, а `onAction="#onSave"` у кнопки вызывает `@FXML`-метод `onSave` своего контроллера.

Имя `fx:id="ordersView"` у `fx:include` позволяет загрузчику передать вложенный контроллер в поле `@FXML private OrdersController ordersViewController`. Аналогично подключаются контроллеры пользователей и статистики. Они создаются через фабрику из `AppContext` и получают тот же контекст.

`initialize()` вызывается после загрузки FXML и внедрения полей с `@FXML`. В `MainController.initialize()` добавляется слушатель выбранной вкладки. Когда пользователь переходит на вкладку, `refresh` вызывает метод нужного вложенного контроллера. Сразу после инициализации обновляется текущая вкладка.

## Зачем разделять FXML и Controller

Разметку окна можно читать и менять отдельно от обработки действий. При этом каждый экран имеет собственный контроллер: заказами управляет `OrdersController`, пользователями — `UsersController`, статистикой — `StatisticsController`. Главное окно лишь переключает вкладки и просит активный экран обновить данные.

## Четыре шага для письма в блокноте

Закрой исходники и попробуй написать свой маленький класс `DemoApplication extends Application` **без подсказок**:

1. Добавь `main(String[] args)`, вызывающий JavaFX `launch(args)`.
2. Переопредели `start(Stage stage)` и загрузи `/main.fxml` через `FXMLLoader`.
3. Получи корень загрузчика и создай `Scene`.
4. Задай заголовок, установи сцену в `Stage` и покажи окно.

После написания объясни вслух: откуда берётся `stage`, когда вызывается `initialize()`, чем путь `/main.fxml` отличается от `/home/.../main.fxml`, зачем в **нашем** приложении используется `ControllerFactory`.

Этот учебный пример предполагает контроллер с пустым конструктором или FXML без контроллера. Реальный `main.fxml` проекта требует `AppContext.load()`, потому что `MainController` получает `AppContext` через конструктор.

<details>
<summary>Пример ответа — открой только после своей попытки</summary>

```java
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class DemoApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/main.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 800, 600);
        stage.setTitle("Демонстрация");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

`start` вызывает JavaFX, `initialize()` вызывается FXMLLoader после внедрения `@FXML`-полей, `/main.fxml` ищется от корня classpath. В youDO конструкторы контроллеров требуют `AppContext`, поэтому применяется фабрика контроллеров.

</details>

## Устный ответ целиком

> Maven выбирает `FxApplication` по `pom.xml`. JavaFX вызывает `start` с главным `Stage`. Приложение создаёт `AppContext`; через `FXMLLoader` он читает `main.fxml` и создаёт контроллеры с общими сервисами. Загрузчик связывает `fx:id` и `@FXML`, затем вызывает `initialize`. Корневой `TabPane` помещается в `Scene`, сцена устанавливается в `Stage`, а `show()` показывает окно. При переходе между вкладками `MainController` вызывает обновление нужного экрана.
