package ru.mirea.freelance.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.util.converter.LocalDateStringConverter;
import ru.mirea.freelance.fx.AppContext;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Objects;

public class OrderFormController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private final AppContext context;
    private Order order;
    private boolean saved;
    private boolean usersLoaded;
    private boolean saving;

    @FXML private Label headerLabel;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<OrderCategory> categoryBox;
    @FXML private TextField budgetField;
    @FXML private DatePicker deadlinePicker;
    @FXML private ComboBox<User> customerBox;
    @FXML private Label customerLabel;
    @FXML private Label infoLabel;
    @FXML private Label errorLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Button retryUsersButton;

    public OrderFormController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        categoryBox.getItems().setAll(OrderCategory.values());
        customerBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(User user) {
                return user == null ? "" : contact(user);
            }

            @Override
            public User fromString(String text) {
                return null;
            }
        });
        deadlinePicker.setConverter(new LocalDateStringConverter(DATE, DATE));
        deadlinePicker.setPromptText("дд.мм.гггг");
        hideError();
    }

    public void setOrder(Order order) {
        this.order = order;
        saved = false;
        usersLoaded = false;
        hideError();
        boolean creating = order == null;
        headerLabel.setText(creating ? "Новый заказ" : "Изменение заказа");
        show(customerBox, creating);
        show(customerLabel, !creating);
        show(infoLabel, !creating);
        if (creating) {
            titleField.clear();
            descriptionArea.clear();
            categoryBox.setValue(null);
            budgetField.clear();
            deadlinePicker.setValue(null);
            customerBox.getItems().clear();
        } else {
            titleField.setText(order.getTitle());
            descriptionArea.setText(order.getDescription());
            categoryBox.setValue(order.getCategory());
            budgetField.setText(order.getBudget().toPlainString());
            deadlinePicker.setValue(order.getDeadline());
        }
        loadUsers();
    }

    public boolean isSaved() {
        return saved;
    }

    public boolean isSaving() {
        return saving;
    }

    @FXML
    private void loadUsers() {
        usersLoaded = false;
        updateSaveButton();
        retryUsersButton.setDisable(true);
        context.tasks().run(() -> context.users().findAll(), this::showUsers, error -> {
            retryUsersButton.setDisable(false);
            showError("Не удалось загрузить пользователей: " + OrdersController.messageOf(error));
        });
    }

    @FXML
    private void onSave() {
        if (saving || !usersLoaded) return;
        hideError();
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        if (title.isEmpty()) {
            fieldError(titleField, "Название: заполните поле.");
            return;
        }
        OrderCategory category = categoryBox.getValue();
        if (category == null) {
            fieldError(categoryBox, "Категория: выберите значение из списка.");
            return;
        }
        BigDecimal budget = readBudget();
        if (budget == null) {
            return;
        }
        LocalDate deadline = readDeadline();
        if (deadline == null) {
            return;
        }
        User customer = customerBox.getValue();
        if (order == null && customer == null) {
            fieldError(customerBox, "Заказчик: выберите заказчика из списка.");
            return;
        }
        String text = descriptionArea.getText();
        String description = text == null || text.isBlank() ? null : text.trim();
        Long orderId = order == null ? null : order.getId();
        Long customerId = customer == null ? null : customer.getId();

        saving = true;
        updateSaveButton();
        context.tasks().run(
                () -> orderId == null
                        ? context.orders().create(title, description, category, budget, deadline, customerId)
                        : context.orders().update(orderId, title, description, category, budget, deadline),
                result -> {
                    saved = true;
                    saving = false;
                    updateSaveButton();
                    close();
                },
                error -> {
                    saving = false;
                    updateSaveButton();
                    showError(OrdersController.messageOf(error));
                });
    }

    @FXML
    private void onCancel() {
        if (!saving) close();
    }

    private void showUsers(List<User> users) {
        hideError();
        retryUsersButton.setDisable(false);
        usersLoaded = true;
        if (order == null) {
            customerBox.getItems().setAll(users.stream()
                    .filter(user -> user.getRole() == UserRole.CUSTOMER)
                    .toList());
            if (customerBox.getItems().isEmpty()) {
                showError("Нет пользователей с ролью «Заказчик». Сначала добавьте заказчика.");
            }
        } else {
            customerLabel.setText(find(users, order.getCustomerId()));
            String freelancer = order.getFreelancerId() == null
                    ? "не назначен" : find(users, order.getFreelancerId());
            infoLabel.setText("Статус: " + order.getStatus() + " · Исполнитель: " + freelancer
                    + ". Статус и исполнитель меняются отдельными действиями.");
        }
        updateSaveButton();
    }

    private BigDecimal readBudget() {
        String text = budgetField.getText() == null ? "" : budgetField.getText()
                .replace(" ", "")
                .replace(" ", "")
                .replace(',', '.');
        if (text.isEmpty()) {
            fieldError(budgetField, "Бюджет: укажите сумму.");
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            fieldError(budgetField, "Бюджет: введите число, например 15000 или 15000,50.");
            return null;
        }
    }

    private LocalDate readDeadline() {
        String text = deadlinePicker.getEditor().getText();
        if (text == null || text.isBlank()) {
            fieldError(deadlinePicker, "Дедлайн: выберите дату.");
            return null;
        }
        try {
            LocalDate date = deadlinePicker.getConverter().fromString(text.trim());
            deadlinePicker.setValue(date);
            return date;
        } catch (DateTimeParseException e) {
            fieldError(deadlinePicker, "Дедлайн: дата в формате дд.мм.гггг, например 15.12.2026.");
            return null;
        }
    }

    private String find(List<User> users, Long id) {
        return users.stream()
                .filter(user -> Objects.equals(user.getId(), id))
                .findFirst()
                .map(this::contact)
                .orElse("—");
    }

    private String contact(User user) {
        return user.getName() + " — " + user.getEmail();
    }

    private void updateSaveButton() {
        saveButton.setDisable(!usersLoaded || saving);
        cancelButton.setDisable(saving);
        retryUsersButton.setDisable(saving);
        titleField.setDisable(saving);
        descriptionArea.setDisable(saving);
        categoryBox.setDisable(saving);
        budgetField.setDisable(saving);
        deadlinePicker.setDisable(saving);
        customerBox.setDisable(saving);
    }

    private void fieldError(Node field, String message) {
        showError(message);
        field.requestFocus();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        show(errorLabel, true);
    }

    private void hideError() {
        show(errorLabel, false);
    }

    private void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void close() {
        ((Stage) saveButton.getScene().getWindow()).close();
    }
}
