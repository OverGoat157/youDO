package ru.mirea.freelance.fx.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import ru.mirea.freelance.fx.AppContext;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.OrderStatus;
import ru.mirea.freelance.model.User;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OrdersController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AppContext context;
    private final ObservableList<Order> orders = FXCollections.observableArrayList();
    private final NumberFormat money = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));
    private Map<Long, User> usersById = Map.of();
    private boolean loading;
    private boolean refreshAgain;

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
    }

    @FXML
    private void onRefresh() {
        refresh();
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

    private void show(Snapshot snapshot) {
        usersById = snapshot.users().stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        orders.setAll(snapshot.orders());
        hideError();
        finish();
    }

    private void showError(Throwable error) {
        String reason = error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
        errorLabel.setText("Не удалось загрузить заказы: " + reason);
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

    private String userName(Long id) {
        User user = id == null ? null : usersById.get(id);
        return user == null ? "—" : user.getName();
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
