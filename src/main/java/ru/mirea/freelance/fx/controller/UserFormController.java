package ru.mirea.freelance.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import ru.mirea.freelance.fx.AppContext;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Форма создания и редактирования пользователя. Правила проверки данных — в UserService, здесь только ввод и показ ошибки. */
public class UserFormController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AppContext context;

    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private ComboBox<UserRole> roleBox;
    @FXML private Label ratingValue;
    @FXML private Label registeredValue;
    @FXML private Label errorLabel;
    @FXML private ProgressIndicator progress;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private User user;
    private boolean saved;
    private boolean saving;

    public UserFormController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        roleBox.getItems().setAll(UserRole.values());
        roleBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(UserRole role) {
                return UsersController.roleTitle(role);
            }

            @Override
            public UserRole fromString(String s) {
                return null;
            }
        });
        roleBox.setValue(UserRole.CUSTOMER);
        setUser(null);
    }

    /** null — создание нового пользователя, иначе форма заполняется текущими значениями. */
    public void setUser(User user) {
        this.user = user;
        saved = false;
        hideError();
        if (user == null) {
            titleLabel.setText("Новый пользователь");
            nameField.clear();
            emailField.clear();
            roleBox.setValue(UserRole.CUSTOMER);
            ratingValue.setText("задаётся при создании");
            registeredValue.setText("задаётся при создании");
        } else {
            titleLabel.setText("Редактирование пользователя");
            nameField.setText(user.getName());
            emailField.setText(user.getEmail());
            roleBox.setValue(user.getRole());
            ratingValue.setText(String.format(Locale.forLanguageTag("ru-RU"), "%.1f", user.getRating()));
            registeredValue.setText(user.getRegisteredAt() == null ? "" : DATE.format(user.getRegisteredAt()));
        }
    }

    public boolean isSaved() {
        return saved;
    }

    public boolean isSaving() {
        return saving;
    }

    @FXML
    private void onSave() {
        if (saving) {
            return;
        }
        // Поля читаем здесь, в UI-потоке: фоновая задача получает уже готовые значения
        String name = nameField.getText();
        String email = emailField.getText();
        UserRole role = roleBox.getValue();
        Long id = user == null ? null : user.getId();

        hideError();
        setSaving(true);
        context.tasks().run(() -> {
            if (id == null) {
                return context.users().create(name, email, role);
            }
            return context.users().update(id, name, email, role);
        }, result -> {
            saved = true;
            setSaving(false);
            close();
        }, error -> {
            // Форма и введённые данные остаются, пользователь может исправить поле
            showError(UsersController.reason(error));
            setSaving(false);
        });
    }

    @FXML
    private void onCancel() {
        if (!saving) {
            close();
        }
    }

    private void setSaving(boolean value) {
        saving = value;
        saveButton.setDisable(value);
        cancelButton.setDisable(value);
        nameField.setDisable(value);
        emailField.setDisable(value);
        roleBox.setDisable(value);
        progress.setVisible(value);
        progress.setManaged(value);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void close() {
        ((Stage) saveButton.getScene().getWindow()).close();
    }
}
