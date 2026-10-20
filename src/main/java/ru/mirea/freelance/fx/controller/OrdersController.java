package ru.mirea.freelance.fx.controller;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import ru.mirea.freelance.fx.AppContext;
import ru.mirea.freelance.fx.Dialogs;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.OrderStatus;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OrdersController {

    private static final String ORDER_FORM = "/ru/mirea/freelance/fx/order-form.fxml";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final AppContext context;
    private final ObservableList<Order> orders = FXCollections.observableArrayList();
    private final NumberFormat money = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
    private final BooleanProperty acting = new SimpleBooleanProperty();
    private Map<Long, User> usersById = Map.of();
    private boolean loading;
    private boolean refreshAgain;

    @FXML private Button createButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button assignButton;
    @FXML private Button statusButton;
    @FXML private Button refreshButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label errorLabel;
    @FXML private TableView<Order> table;
    @FXML private TableColumn<Order, String> titleColumn;
    @FXML private TableColumn<Order, OrderCategory> categoryColumn;
    @FXML private TableColumn<Order, BigDecimal> budgetColumn;
    @FXML private TableColumn<Order, LocalDate> deadlineColumn;
    @FXML private TableColumn<Order, OrderStatus> statusColumn;
    @FXML private TableColumn<Order, String> customerColumn;
    @FXML private TableColumn<Order, String> freelancerColumn;
    @FXML private Label noSelectionLabel;
    @FXML private GridPane details;
    @FXML private Label descriptionLabel;
    @FXML private Label customerLabel;
    @FXML private Label freelancerLabel;
    @FXML private Label createdLabel;

    private record Snapshot(List<Order> orders, List<User> users) {
    }

    public OrdersController(AppContext context) {
        this.context = context;
        money.setMinimumFractionDigits(2);
        money.setMaximumFractionDigits(2);
    }

    @FXML
    private void initialize() {
        titleColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getTitle()));
        categoryColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getCategory()));
        budgetColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getBudget()));
        budgetColumn.setCellFactory(column -> formattedCell(money::format));
        deadlineColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getDeadline()));
        deadlineColumn.setCellFactory(column -> formattedCell(DATE::format));
        statusColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getStatus()));
        customerColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                userName(cell.getValue().getCustomerId())));
        freelancerColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                userName(cell.getValue().getFreelancerId())));
        table.setItems(orders);
        table.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> showDetails(selected));
        showDetails(null);

        BooleanBinding unavailable = table.getSelectionModel().selectedItemProperty().isNull().or(acting);
        createButton.disableProperty().bind(acting);
        editButton.disableProperty().bind(unavailable);
        deleteButton.disableProperty().bind(unavailable);
        assignButton.disableProperty().bind(unavailable);
        statusButton.disableProperty().bind(unavailable);
    }

    public List<Order> currentOrders() {
        return List.copyOf(table.getItems());
    }

    public void refresh() {
        if (loading) {
            refreshAgain = true;
            return;
        }
        loading = true;
        setBusy(true);
        context.tasks().run(
                () -> new Snapshot(context.orders().findAll(), context.users().findAll()),
                this::show,
                this::showError);
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void onCreate() {
        openForm(null);
    }

    @FXML
    private void onEdit() {
        openForm(selected());
    }

    @FXML
    private void onDelete() {
        Order order = selected();
        if (!Dialogs.confirmDelete(window(), order.getTitle())) {
            return;
        }
        runAction(() -> {
            context.orders().delete(order.getId());
            return order;
        });
    }

    @FXML
    private void onAssign() {
        Order order = selected();
        if (order.getStatus().isFinal()) {
            info("Заказ в статусе " + order.getStatus() + ", исполнителя назначить нельзя.");
            return;
        }
        List<User> freelancers = usersById.values().stream()
                .filter(user -> user.getRole() == UserRole.FREELANCER)
                .sorted(Comparator.comparing(User::getName))
                .toList();
        if (freelancers.isEmpty()) {
            info("Нет пользователей с ролью «Фрилансер». Сначала добавьте исполнителя.");
            return;
        }
        User current = order.getFreelancerId() == null ? null : usersById.get(order.getFreelancerId());
        choose("Назначить исполнителя", "Заказ «" + order.getTitle() + "»", freelancers, current, this::contact)
                .ifPresent(freelancer -> runAction(
                        () -> context.orders().assignFreelancer(order.getId(), freelancer.getId())));
    }

    @FXML
    private void onChangeStatus() {
        Order order = selected();
        OrderStatus current = order.getStatus();
        List<OrderStatus> allowed = Arrays.stream(OrderStatus.values())
                .filter(current::canTransitionTo)
                .toList();
        if (allowed.isEmpty()) {
            info("Заказ в финальном статусе " + current + ", статус больше не меняется.");
            return;
        }
        choose("Сменить статус", "Заказ «" + order.getTitle() + "», сейчас " + current,
                allowed, null, OrderStatus::name)
                .ifPresent(next -> runAction(() -> context.orders().changeStatus(order.getId(), next)));
    }

    @FXML
    private void onExport() {
        context.exportAction().exportOrders(window(), currentOrders(), List.copyOf(usersById.values()));
    }

    static String messageOf(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private void openForm(Order order) {
        FXMLLoader loader;
        try {
            loader = context.load(ORDER_FORM);
        } catch (RuntimeException e) {
            Dialogs.error(window(), messageOf(e));
            return;
        }
        OrderFormController form = loader.getController();
        Scene scene = new Scene(loader.<Parent>getRoot());
        scene.getStylesheets().setAll(table.getScene().getStylesheets());
        Stage stage = new Stage();
        stage.initOwner(window());
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(order == null ? "Новый заказ" : "Изменение заказа");
        stage.setMinWidth(420);
        stage.setMinHeight(420);
        stage.setScene(scene);
        form.setOrder(order);
        stage.showAndWait();
        if (form.isSaved()) {
            refresh();
        }
    }

    private <T> void runAction(Callable<T> work) {
        acting.set(true);
        context.tasks().run(work,
                result -> {
                    acting.set(false);
                    refresh();
                },
                error -> {
                    acting.set(false);
                    Dialogs.error(window(), messageOf(error));
                });
    }

    private <T> Optional<T> choose(String title, String header, List<T> items, T current,
                                   Function<T, String> label) {
        ComboBox<T> box = new ComboBox<>(FXCollections.observableArrayList(items));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        });
        box.setValue(current);
        box.setMaxWidth(Double.MAX_VALUE);

        ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<T> dialog = new Dialog<>();
        dialog.initOwner(window());
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getStylesheets().setAll(table.getScene().getStylesheets());
        dialog.getDialogPane().getButtonTypes().setAll(save, cancel);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().setPrefWidth(420);
        dialog.getDialogPane().lookupButton(save).disableProperty().bind(box.valueProperty().isNull());
        dialog.setResultConverter(button -> button == save ? box.getValue() : null);
        return dialog.showAndWait();
    }

    private void show(Snapshot snapshot) {
        Order selected = selected();
        Long selectedId = selected == null ? null : selected.getId();
        usersById = snapshot.users().stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        orders.setAll(snapshot.orders());
        table.sort();
        restoreSelection(selectedId);
        hideError();
        finish();
    }

    private void restoreSelection(Long id) {
        orders.stream()
                .filter(order -> order.getId().equals(id))
                .findFirst()
                .ifPresentOrElse(order -> {
                    table.getSelectionModel().select(order);
                    table.scrollTo(order);
                }, () -> table.getSelectionModel().clearSelection());
    }

    private void showDetails(Order order) {
        boolean selected = order != null;
        details.setVisible(selected);
        details.setManaged(selected);
        noSelectionLabel.setVisible(!selected);
        noSelectionLabel.setManaged(!selected);
        if (!selected) {
            return;
        }
        String description = order.getDescription();
        descriptionLabel.setText(description == null || description.isBlank() ? "Без описания" : description);
        customerLabel.setText(userContact(order.getCustomerId()));
        freelancerLabel.setText(order.getFreelancerId() == null
                ? "Не назначен" : userContact(order.getFreelancerId()));
        createdLabel.setText(order.getCreatedAt() == null ? "—" : DATE_TIME.format(order.getCreatedAt()));
    }

    private void showError(Throwable error) {
        errorLabel.setText("Не удалось загрузить заказы: " + messageOf(error));
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        finish();
    }

    private void finish() {
        loading = false;
        setBusy(false);
        if (refreshAgain) {
            refreshAgain = false;
            refresh();
        }
    }

    private void setBusy(boolean busy) {
        refreshButton.setDisable(busy);
        progress.setVisible(busy);
        progress.setManaged(busy);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.initOwner(window());
        alert.setTitle("Заказы");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private Order selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    private Window window() {
        return table.getScene().getWindow();
    }

    private String userName(Long id) {
        User user = id == null ? null : usersById.get(id);
        return user == null ? "—" : user.getName();
    }

    private String userContact(Long id) {
        User user = id == null ? null : usersById.get(id);
        return user == null ? "—" : contact(user);
    }

    private String contact(User user) {
        return user.getName() + " — " + user.getEmail();
    }

    private static <T> TableCell<Order, T> formattedCell(Function<T, String> format) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : format.apply(value));
            }
        };
    }
}
