package ru.mirea.freelance.fx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import ru.mirea.freelance.exception.DatabaseException;
import ru.mirea.freelance.fx.controller.OrderFormController;
import ru.mirea.freelance.fx.controller.OrdersController;
import ru.mirea.freelance.model.*;
import ru.mirea.freelance.repository.InMemoryOrderRepository;
import ru.mirea.freelance.repository.InMemoryUserRepository;
import ru.mirea.freelance.util.OrderCsvExporter;
import ru.mirea.freelance.util.DatabaseManager;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static ru.mirea.freelance.fx.FxTestSupport.*;

@EnabledIfSystemProperty(named = "youdo.fx.tests", matches = "true")
class OrdersUiTest {
    @TempDir Path directory;

    @BeforeAll
    static void initializeToolkit() throws Exception { startToolkit(); }

    private record View(AppContext context, FXMLLoader loader, Stage stage) implements AutoCloseable {
        Parent root() { return loader.getRoot(); }
        @SuppressWarnings("unchecked")
        <T extends Node> T node(String id) { return (T) root().lookup("#" + id); }
        Button button(String id) { return node(id); }
        TextField field(String id) { return node(id); }
        @SuppressWarnings("unchecked")
        <T> ComboBox<T> box(String id) { return node(id); }
        OrdersController orders() { return loader.getController(); }
        @Override public void close() throws Exception {
            onFx(() -> {
                for (Window window : new ArrayList<>(Window.getWindows())) {
                    if (window instanceof Stage child && child.getOwner() == stage) window.hide();
                }
                stage.hide();
                return null;
            });
            context.close();
        }
    }

    private View open(AppContext context, String name) throws Exception {
        return onFx(() -> {
            FXMLLoader loader = context.load("/ru/mirea/freelance/fx/" + name);
            Stage stage = new Stage();
            Scene scene = new Scene(loader.<Parent>getRoot(), 1100, 760);
            scene.getStylesheets().add(getClass().getResource("/ru/mirea/freelance/fx/app.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            return new View(context, loader, stage);
        });
    }

    private AppContext context() {
        AppContext context = new AppContext(new InMemoryUserRepository(), new InMemoryOrderRepository());
        addPeople(context);
        return context;
    }

    private void addPeople(AppContext context) {
        context.users().create("Анна", "anna@example.org", UserRole.CUSTOMER);
        context.users().create("Иван", "ivan@example.org", UserRole.FREELANCER);
    }

    private User person(AppContext context, UserRole role) {
        return context.users().findAll().stream().filter(user -> user.getRole() == role).findFirst().orElseThrow();
    }

    private Order order(AppContext context, String title, String description, OrderCategory category,
                        String budget, int days) {
        return context.orders().create(title, description, category, new BigDecimal(budget),
                LocalDate.now().plusDays(days), person(context, UserRole.CUSTOMER).getId());
    }

    private void drain(AppContext context) throws Exception {
        // Первый callback может поставить повторное чтение (последние условия поиска) в очередь.
        finishTasks(context);
        finishTasks(context);
    }

    @Test
    void mainLoadsAllThreeScreensAndRefreshesTabs() throws Exception {
        AppContext context = context();
        order(context, "Сайт", "Описание", OrderCategory.DEVELOPMENT, "10", 2);
        try (View view = open(context, "main.fxml")) {
            drain(context);
            onFx(() -> {
                TabPane tabs = (TabPane) view.root();
                assertEquals(List.of("Заказы", "Пользователи", "Статистика"),
                        tabs.getTabs().stream().map(Tab::getText).toList());
                Parent orders = (Parent) view.loader().getNamespace().get("ordersView");
                assertEquals(1, ((TableView<?>) orders.lookup("#table")).getItems().size());
                tabs.getSelectionModel().select(1);
                return null;
            });
            drain(context);
            onFx(() -> {
                Parent users = (Parent) view.loader().getNamespace().get("usersView");
                assertEquals(2, ((TableView<?>) users.lookup("#table")).getItems().size());
                ((TabPane) view.root()).getSelectionModel().select(2);
                return null;
            });
            drain(context);
            onFx(() -> {
                Parent stats = (Parent) view.loader().getNamespace().get("statisticsView");
                assertEquals(13, ((TableView<?>) stats.lookup("#table")).getItems().size());
                return null;
            });
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void combinedFiltersSortingRefreshResetAndCsvUseTheVisibleRows() throws Exception {
        AppContext context = context();
        Order first = order(context, "Логотип", "САЙТ кофейни", OrderCategory.DESIGN, "1000", 1);
        Order second = order(context, "Сайт", null, OrderCategory.DESIGN, "20", 3);
        order(context, "Сайт другой категории", "", OrderCategory.OTHER, "3", 2);
        Order active = order(context, "Сайт в работе", "", OrderCategory.DESIGN, "9", 4);
        context.orders().assignFreelancer(active.getId(), person(context, UserRole.FREELANCER).getId());
        context.orders().changeStatus(active.getId(), OrderStatus.IN_PROGRESS);
        order(context, "Другое", null, OrderCategory.DESIGN, "4", 5);
        try (View view = open(context, "orders.fxml")) {
            onFx(() -> {
                view.field("searchField").setText("  сайт  ");
                view.<OrderStatus>box("statusFilter").setValue(OrderStatus.OPEN);
                view.<OrderCategory>box("categoryFilter").setValue(OrderCategory.DESIGN);
                view.button("searchButton").fire();
                return null;
            });
            drain(context);
            List<Order> visible = onFx(() -> {
                TableView<Order> table = view.node("table");
                TableColumn<Order, BigDecimal> budget = (TableColumn<Order, BigDecimal>) table.getColumns().get(2);
                budget.setSortType(TableColumn.SortType.DESCENDING);
                table.getSortOrder().setAll(budget);
                table.sort();
                assertEquals(List.of(first, second), view.orders().currentOrders());
                assertEquals("Найдено: 2", ((Label) view.node("countLabel")).getText());
                table.getSelectionModel().select(0);
                assertEquals("САЙТ кофейни", ((Label) view.node("descriptionLabel")).getText());
                view.orders().refresh();
                return view.orders().currentOrders();
            });
            drain(context);
            onFx(() -> {
                assertEquals(List.of(first, second), view.orders().currentOrders());
                TableView<Order> table = view.node("table");
                assertEquals(first.getId(), table.getSelectionModel().getSelectedItem().getId());
                TableColumn<Order, LocalDate> dates = (TableColumn<Order, LocalDate>) table.getColumns().get(3);
                dates.setSortType(TableColumn.SortType.DESCENDING);
                table.getSortOrder().setAll(dates);
                table.sort();
                assertEquals(List.of(second, first), view.orders().currentOrders());
                return null;
            });
            Path csv = directory.resolve("visible.csv");
            new OrderCsvExporter().export(visible, context.users().findAll(), csv);
            List<String> lines = Files.readAllLines(csv);
            assertEquals(3, lines.size());
            assertTrue(lines.get(1).startsWith("Логотип;"));
            assertTrue(lines.get(2).startsWith("Сайт;"));
            assertFalse(Files.readString(csv).contains("другой категории"));
            onFx(() -> { view.button("resetButton").fire(); return null; });
            drain(context);
            onFx(() -> {
                assertEquals(5, view.orders().currentOrders().size());
                assertTrue(view.field("searchField").getText().isEmpty());
                assertNull(view.box("statusFilter").getValue());
                assertNull(view.box("categoryFilter").getValue());
                return null;
            });
        }
    }

    @Test
    void newerSearchDiscardsTheOldResultAndDisablesActionsWhileLoading() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean firstRead = new AtomicBoolean(true);
        InMemoryOrderRepository repository = new InMemoryOrderRepository() {
            @Override public List<Order> findAll() {
                List<Order> snapshot = super.findAll();
                if (firstRead.getAndSet(false)) {
                    started.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Чтение не разблокировано");
                    } catch (InterruptedException error) { throw new IllegalStateException(error); }
                }
                return snapshot;
            }
        };
        AppContext context = new AppContext(new InMemoryUserRepository(), repository);
        addPeople(context);
        order(context, "Старый", "", OrderCategory.DESIGN, "1", 1);
        Order newest = order(context, "Новый", "", OrderCategory.DESIGN, "2", 1);
        try (View view = open(context, "orders.fxml")) {
            List<String> seen = new ArrayList<>();
            onFx(() -> {
                TableView<Order> table = view.node("table");
                table.getItems().addListener((javafx.collections.ListChangeListener<Order>) change ->
                        seen.addAll(table.getItems().stream().map(Order::getTitle).toList()));
                view.field("searchField").setText("Старый");
                view.button("searchButton").fire();
                return null;
            });
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                onFx(() -> {
                    assertTrue(view.button("createButton").isDisabled());
                    assertTrue(view.button("exportButton").isDisabled());
                    view.field("searchField").setText("Новый");
                    view.button("searchButton").fire();
                    return null;
                });
            } finally { release.countDown(); }
            drain(context);
            onFx(() -> {
                assertEquals(List.of(newest), view.orders().currentOrders());
                assertFalse(seen.contains("Старый"));
                assertFalse(view.button("exportButton").isDisabled());
                return null;
            });
        }
    }

    private void fill(View view, String title, String budget, String date) {
        view.field("titleField").setText(title);
        view.field("budgetField").setText(budget);
        view.<OrderCategory>box("categoryBox").setValue(OrderCategory.DESIGN);
        view.box("customerBox").getSelectionModel().selectFirst();
        DatePicker picker = view.node("deadlinePicker");
        picker.getEditor().setText(date);
    }

    @Test
    void formRejectsInvalidCalendarDateAndKopecksThenSavesLocalizedMoney() throws Exception {
        AppContext context = context();
        try (View view = open(context, "order-form.fxml")) {
            OrderFormController form = view.loader().getController();
            onFx(() -> { form.setOrder(null); return null; });
            drain(context);
            String future = LocalDate.now().plusDays(10).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            onFx(() -> {
                assertTrue(view.<User>box("customerBox").getItems().stream().allMatch(u -> u.getRole() == UserRole.CUSTOMER));
                fill(view, "Новый заказ", "12,345", future);
                view.button("saveButton").fire();
                return null;
            });
            drain(context);
            onFx(() -> {
                assertTrue(view.stage().isShowing());
                assertFalse(form.isSaved());
                assertTrue(((Label) view.node("errorLabel")).getText().contains("двух знаков"));
                assertEquals("12,345", view.field("budgetField").getText());
                fill(view, "Новый заказ", "1 234,50", "31.02." + (LocalDate.now().getYear() + 1));
                view.button("saveButton").fire();
                assertTrue(((Label) view.node("errorLabel")).getText().contains("Дедлайн"));
                assertFalse(form.isSaved());
                fill(view, "Новый заказ", "1 234,50", future);
                view.button("saveButton").fire();
                return null;
            });
            drain(context);
            onFx(() -> {
                assertTrue(form.isSaved());
                assertFalse(view.stage().isShowing());
                return null;
            });
            assertEquals(new BigDecimal("1234.50"), context.orders().findAll().getFirst().getBudget());
        }
    }

    @Test
    void editingPastDeadlinePreservesInputAndCancelDoesNotWrite() throws Exception {
        AppContext context = context();
        Order original = order(context, "Старое название", "Описание", OrderCategory.DESIGN, "12", 1);
        original.setDeadline(LocalDate.now().minusDays(1));
        try (View view = open(context, "order-form.fxml")) {
            OrderFormController form = view.loader().getController();
            onFx(() -> { form.setOrder(original); return null; });
            drain(context);
            onFx(() -> {
                assertEquals("Старое название", view.field("titleField").getText());
                assertFalse(view.node("customerBox").isVisible());
                view.field("titleField").setText("Новое название");
                view.button("saveButton").fire();
                return null;
            });
            drain(context);
            onFx(() -> {
                assertTrue(((Label) view.node("errorLabel")).getText().contains("прошлом"));
                assertEquals("Новое название", view.field("titleField").getText());
                view.button("cancelButton").fire();
                assertFalse(form.isSaved());
                return null;
            });
            assertEquals("Старое название", context.orders().getById(original.getId()).getTitle());
        }
    }

    private DialogPane dialog(Stage owner) throws Exception {
        return onFx(() -> Window.getWindows().stream()
                .filter(window -> window instanceof Stage child && child.getOwner() == owner)
                .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast).findFirst().orElseThrow());
    }

    private void answer(DialogPane dialog, ButtonBar.ButtonData data) {
        ButtonType type = dialog.getButtonTypes().stream().filter(button -> button.getButtonData() == data)
                .findFirst().orElseThrow();
        ((Button) dialog.lookupButton(type)).fire();
    }

    @Test
    void assignmentStatusWorkflowAndDeleteConfirmationWorkThroughTheScreen() throws Exception {
        AppContext context = context();
        Order original = order(context, "Заказ для действий", "", OrderCategory.DESIGN, "12", 1);
        try (View view = open(context, "orders.fxml")) {
            onFx(() -> { view.orders().refresh(); return null; });
            drain(context);
            onFx(() -> {
                ((TableView<?>) view.node("table")).getSelectionModel().selectFirst();
                Platform.runLater(() -> view.button("assignButton").fire());
                return null;
            });
            DialogPane assign = dialog(view.stage());
            onFx(() -> {
                ComboBox<?> box = (ComboBox<?>) assign.getContent();
                assertTrue(box.getItems().stream().allMatch(item -> ((User) item).getRole() == UserRole.FREELANCER));
                box.getSelectionModel().selectFirst();
                answer(assign, ButtonBar.ButtonData.OK_DONE);
                return null;
            });
            drain(context);
            assertNotNull(context.orders().getById(original.getId()).getFreelancerId());
            for (OrderStatus next : List.of(OrderStatus.IN_PROGRESS, OrderStatus.ON_REVIEW, OrderStatus.COMPLETED)) {
                Platform.runLater(() -> view.button("statusButton").fire());
                DialogPane status = dialog(view.stage());
                onFx(() -> {
                    @SuppressWarnings("unchecked") ComboBox<OrderStatus> box = (ComboBox<OrderStatus>) status.getContent();
                    assertTrue(box.getItems().contains(next));
                    box.setValue(next);
                    answer(status, ButtonBar.ButtonData.OK_DONE);
                    return null;
                });
                drain(context);
                assertEquals(next, context.orders().getById(original.getId()).getStatus());
            }
            Platform.runLater(() -> view.button("deleteButton").fire());
            DialogPane cancel = dialog(view.stage());
            onFx(() -> { answer(cancel, ButtonBar.ButtonData.CANCEL_CLOSE); return null; });
            assertEquals(1, context.orders().findAll().size());
            Platform.runLater(() -> view.button("deleteButton").fire());
            DialogPane remove = dialog(view.stage());
            onFx(() -> { answer(remove, ButtonBar.ButtonData.OK_DONE); return null; });
            drain(context);
            assertTrue(context.orders().findAll().isEmpty());
            onFx(() -> { assertTrue(view.orders().currentOrders().isEmpty()); return null; });
        }
    }

    @Test
    void orderModalCannotCloseDuringSaveAndTheParentReloads() throws Exception {
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        InMemoryOrderRepository repository = new InMemoryOrderRepository() {
            @Override public Order save(Order order) {
                writing.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Запись не разблокирована");
                } catch (InterruptedException error) { throw new IllegalStateException(error); }
                return super.save(order);
            }
        };
        AppContext context = new AppContext(new InMemoryUserRepository(), repository);
        addPeople(context);
        try (View view = open(context, "orders.fxml")) {
            Platform.runLater(() -> view.button("createButton").fire());
            Stage modal = onFx(() -> (Stage) Window.getWindows().stream()
                    .filter(window -> window instanceof Stage child && child.getOwner() == view.stage())
                    .findFirst().orElseThrow());
            drain(context);
            onFx(() -> {
                Parent root = modal.getScene().getRoot();
                ((TextField) root.lookup("#titleField")).setText("Проверка записи");
                ((TextField) root.lookup("#budgetField")).setText("25,50");
                @SuppressWarnings("unchecked") ComboBox<OrderCategory> category =
                        (ComboBox<OrderCategory>) root.lookup("#categoryBox");
                category.setValue(OrderCategory.DESIGN);
                ((ComboBox<?>) root.lookup("#customerBox")).getSelectionModel().selectFirst();
                ((DatePicker) root.lookup("#deadlinePicker")).getEditor().setText(
                        LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
                ((Button) root.lookup("#saveButton")).fire();
                return null;
            });
            try {
                assertTrue(writing.await(5, TimeUnit.SECONDS));
                onFx(() -> {
                    WindowEvent close = new WindowEvent(modal, WindowEvent.WINDOW_CLOSE_REQUEST);
                    modal.getOnCloseRequest().handle(close);
                    assertTrue(close.isConsumed());
                    assertTrue(modal.isShowing());
                    assertTrue(modal.getScene().getRoot().lookup("#cancelButton").isDisabled());
                    return null;
                });
            } finally { release.countDown(); }
            drain(context);
            onFx(() -> {
                assertFalse(modal.isShowing());
                assertEquals(1, view.orders().currentOrders().size());
                return null;
            });
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "youdo.db.tests", matches = "true")
    void actualDatabaseOrderCanBeFilteredEditedAndReloadedThroughFxml() throws Exception {
        AppContext context = new AppContext(new DatabaseManager(Path.of(System.getProperty("youdo.test.db.config"))));
        String key = java.util.UUID.randomUUID().toString();
        User customer = context.users().create("Проверка экрана", key + "@example.org", UserRole.CUSTOMER);
        Order original = null;
        try {
            original = context.orders().create(key, "Описание", OrderCategory.DESIGN,
                    new BigDecimal("10.00"), LocalDate.now().plusDays(5), customer.getId());
            long id = original.getId();
            try (View view = open(context, "orders.fxml")) {
                onFx(() -> {
                    view.field("searchField").setText(key);
                    view.button("searchButton").fire();
                    return null;
                });
                drain(context);
                onFx(() -> {
                    assertEquals(1, view.orders().currentOrders().size());
                    ((TableView<?>) view.node("table")).getSelectionModel().selectFirst();
                    Platform.runLater(() -> view.button("editButton").fire());
                    return null;
                });
                Stage modal = onFx(() -> (Stage) Window.getWindows().stream()
                        .filter(window -> window instanceof Stage child && child.getOwner() == view.stage())
                        .findFirst().orElseThrow());
                drain(context);
                onFx(() -> {
                    Parent root = modal.getScene().getRoot();
                    ((TextField) root.lookup("#budgetField")).setText("1 234,50");
                    ((Button) root.lookup("#saveButton")).fire();
                    return null;
                });
                drain(context);
                onFx(() -> {
                    assertFalse(modal.isShowing());
                    assertEquals(new BigDecimal("1234.50"), view.orders().currentOrders().getFirst().getBudget());
                    assertEquals(key, view.field("searchField").getText());
                    return null;
                });
                assertEquals(new BigDecimal("1234.50"), context.orders().getById(id).getBudget());
            }
        } finally {
            // View закрывает фоновые задачи; синхронные сервисы остаются доступны для удаления своих данных.
            if (original != null) context.orders().delete(original.getId());
            context.users().delete(customer.getId());
            context.close();
        }
    }

    @Test
    void formCanRetryUserLoadingWithoutLosingFields() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        InMemoryUserRepository repository = new InMemoryUserRepository() {
            @Override public List<User> findAll() {
                if (fail.getAndSet(false)) throw new DatabaseException("БД недоступна");
                return super.findAll();
            }
        };
        AppContext context = new AppContext(repository, new InMemoryOrderRepository());
        context.users().create("Анна", "anna@example.org", UserRole.CUSTOMER);
        try (View view = open(context, "order-form.fxml")) {
            OrderFormController form = view.loader().getController();
            onFx(() -> { form.setOrder(null); return null; });
            drain(context);
            onFx(() -> {
                assertTrue(view.button("saveButton").isDisabled());
                assertFalse(view.button("retryUsersButton").isDisabled());
                view.field("titleField").setText("Сохранённый ввод");
                view.button("retryUsersButton").fire();
                return null;
            });
            drain(context);
            onFx(() -> {
                assertEquals("Сохранённый ввод", view.field("titleField").getText());
                assertFalse(view.button("saveButton").isDisabled());
                assertEquals(1, view.box("customerBox").getItems().size());
                return null;
            });
        }
    }
}
