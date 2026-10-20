package ru.mirea.freelance.fx.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import ru.mirea.freelance.fx.AppContext;
import ru.mirea.freelance.fx.Dialogs;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Вкладка «Пользователи»: список и действия над ним. SQL и проверки данных остаются в слоях ниже. */
public class UsersController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String FORM_FXML = "/ru/mirea/freelance/fx/user-form.fxml";

    private final AppContext context;

    @FXML private TableView<User> table;
    @FXML private TableColumn<User, String> nameColumn;
    @FXML private TableColumn<User, String> emailColumn;
    @FXML private TableColumn<User, String> roleColumn;
    @FXML private TableColumn<User, String> ratingColumn;
    @FXML private TableColumn<User, LocalDate> registeredColumn;
    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button refreshButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label errorLabel;

    private final ObservableList<User> users = FXCollections.observableArrayList();
    private boolean busy;

    public UsersController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getName()));
        emailColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getEmail()));
        roleColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(roleTitle(c.getValue().getRole())));
        ratingColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                String.format(Locale.forLanguageTag("ru-RU"), "%.1f", c.getValue().getRating())));
        registeredColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getRegisteredAt()));
        registeredColumn.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : DATE.format(item));
            }
        });
        table.setItems(users);
        table.setPlaceholder(new Label("Пользователей нет"));
        // Изменение и удаление доступны только при выбранной строке и вне операции
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> updateButtons());
        updateButtons();
    }

    /** Перечитывает пользователей из БД. Главное окно вызывает его при переходе на вкладку. */
    public void refresh() {
        if (busy) {
            return;
        }
        setBusy(true);
        // Чтение БД — в фоне; ObservableList меняется только в callback, то есть в UI-потоке
        context.tasks().run(() -> context.users().findAll(), this::show, this::showLoadError);
    }

    private void show(List<User> loaded) {
        Long selectedId = table.getSelectionModel().getSelectedItem() == null
                ? null : table.getSelectionModel().getSelectedItem().getId();
        users.setAll(loaded);
        table.sort();
        if (selectedId != null) {
            users.stream().filter(u -> selectedId.equals(u.getId())).findFirst()
                    .ifPresent(u -> table.getSelectionModel().select(u));
        }
        hideError();
        setBusy(false);
    }

    private void showLoadError(Throwable error) {
        // Прежний список остаётся на экране, сверху — причина без stack trace
        errorLabel.setText("Не удалось загрузить пользователей: " + reason(error));
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        setBusy(false);
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onAdd() {
        openForm(null);
    }

    @FXML
    private void onEdit() {
        User selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openForm(selected);
        }
    }

    @FXML
    private void onDelete() {
        User selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || busy) {
            return;
        }
        Window owner = table.getScene().getWindow();
        if (!Dialogs.confirmDelete(owner, selected.getName())) {
            return;
        }
        Long id = selected.getId();
        setBusy(true);
        context.tasks().run(() -> {
            context.users().delete(id);
            return Boolean.TRUE;
        }, ok -> {
            setBusy(false);
            refresh();
        }, error -> {
            setBusy(false);
            // Сообщение сервиса: например, у пользователя есть активные заказы
            Dialogs.error(owner, reason(error));
        });
    }

    /** Открывает модальную форму; после сохранения перечитывает список. */
    private void openForm(User user) {
        if (busy) {
            return;
        }
        Window owner = table.getScene().getWindow();
        try {
            // AppContext.load создаёт контроллер с контекстом и уже загружает FXML
            FXMLLoader loader = context.load(FORM_FXML);
            Parent root = loader.getRoot();
            UserFormController form = loader.getController();
            form.setUser(user);

            Stage stage = new Stage();
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setTitle(user == null ? "Новый пользователь" : "Редактирование пользователя");
            Scene scene = new Scene(root);
            // Тот же CSS, что у основной сцены: отдельное окно сам по себе его не наследует
            scene.getStylesheets().addAll(table.getScene().getStylesheets());
            stage.setScene(scene);
            stage.setMinWidth(420);
            stage.setOnCloseRequest(event -> {
                if (form.isSaving()) {
                    event.consume();
                }
            });
            stage.showAndWait();

            if (form.isSaved()) {
                refresh();
            }
        } catch (RuntimeException e) {
            Dialogs.error(owner, "Не удалось открыть форму: " + reason(e));
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisible(value);
        progress.setManaged(value);
        updateButtons();
    }

    private void updateButtons() {
        boolean noSelection = table.getSelectionModel().getSelectedItem() == null;
        addButton.setDisable(busy);
        refreshButton.setDisable(busy);
        editButton.setDisable(busy || noSelection);
        deleteButton.setDisable(busy || noSelection);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    static String reason(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    static String roleTitle(UserRole role) {
        if (role == null) {
            return "";
        }
        return switch (role) {
            case CUSTOMER -> "Заказчик";
            case FREELANCER -> "Исполнитель";
        };
    }
}
