package ru.mirea.freelance.fx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.mirea.freelance.fx.controller.UserFormController;
import ru.mirea.freelance.fx.controller.UsersController;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.repository.InMemoryOrderRepository;
import ru.mirea.freelance.repository.InMemoryUserRepository;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static ru.mirea.freelance.fx.FxTestSupport.*;

@EnabledIfSystemProperty(named = "youdo.fx.tests", matches = "true")
class UsersUiTest {
    @BeforeAll
    static void initializeToolkit() throws Exception {
        startToolkit();
    }

    private record View(AppContext context, FXMLLoader loader, Stage stage) implements AutoCloseable {
        Parent root() { return loader.getRoot(); }
        Button button(String id) { return (Button) root().lookup("#" + id); }
        TextField field(String id) { return (TextField) root().lookup("#" + id); }

        @Override
        public void close() throws Exception {
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

    private View open(AppContext context, String resource) throws Exception {
        return onFx(() -> {
            FXMLLoader loader = context.load("/ru/mirea/freelance/fx/" + resource);
            Stage stage = new Stage();
            Scene scene = new Scene(loader.<Parent>getRoot());
            scene.getStylesheets().add(getClass().getResource("/ru/mirea/freelance/fx/app.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            return new View(context, loader, stage);
        });
    }

    @Test
    void tableLoadsAndActionsFollowSelection() throws Exception {
        try (View view = open(new AppContext(new InMemoryUserRepository(), new InMemoryOrderRepository()), "users.fxml")) {
            UsersController controller = view.loader().getController();
            onFx(() -> { controller.refresh(); return null; });
            finishTasks(view.context());
            onFx(() -> {
                assertTrue(view.button("editButton").isDisabled());
                assertTrue(view.button("deleteButton").isDisabled());
                assertFalse(view.button("addButton").isDisabled());
                return null;
            });
            User user = view.context().users().create("Анна", "anna@example.org", UserRole.CUSTOMER);
            onFx(() -> { controller.refresh(); return null; });
            finishTasks(view.context());
            onFx(() -> {
                TableView<?> table = (TableView<?>) view.root().lookup("#table");
                assertEquals(1, table.getItems().size());
                assertEquals(user, table.getItems().getFirst());
                table.getSelectionModel().select(0);
                assertFalse(view.button("editButton").isDisabled());
                assertFalse(view.button("deleteButton").isDisabled());
                return null;
            });
        }
    }

    @Test
    void validationKeepsInputAndRetrySavesTheForm() throws Exception {
        AppContext context = new AppContext(new InMemoryUserRepository(), new InMemoryOrderRepository());
        context.users().create("Анна", "anna@example.org", UserRole.CUSTOMER);
        try (View view = open(context, "user-form.fxml")) {
            UserFormController controller = view.loader().getController();
            onFx(() -> {
                view.field("nameField").setText("Новый пользователь");
                view.field("emailField").setText("anna@example.org");
                view.button("saveButton").fire();
                return null;
            });
            finishTasks(context);
            onFx(() -> {
                assertTrue(view.stage().isShowing());
                assertFalse(controller.isSaved());
                assertTrue(((Label) view.root().lookup("#errorLabel")).getText().contains("уже существует"));
                assertEquals("Новый пользователь", view.field("nameField").getText());
                assertEquals("anna@example.org", view.field("emailField").getText());
                assertFalse(view.button("saveButton").isDisabled());
                view.field("emailField").setText("new@example.org");
                view.button("saveButton").fire();
                return null;
            });
            finishTasks(context);
            onFx(() -> {
                assertTrue(controller.isSaved());
                assertFalse(view.stage().isShowing());
                controller.setUser(null);
                assertFalse(controller.isSaved());
                return null;
            });
            assertEquals(2, context.users().findAll().size());
        }
    }

    @Test
    void editingIsPrefilledAndCancelDoesNotSave() throws Exception {
        AppContext context = new AppContext(new InMemoryUserRepository(), new InMemoryOrderRepository());
        User original = context.users().create("Анна", "anna@example.org", UserRole.CUSTOMER);
        try (View view = open(context, "user-form.fxml")) {
            UserFormController controller = view.loader().getController();
            onFx(() -> {
                controller.setUser(original);
                assertEquals(original.getName(), view.field("nameField").getText());
                assertEquals(original.getEmail(), view.field("emailField").getText());
                view.field("nameField").setText("Изменённое имя");
                view.button("cancelButton").fire();
                assertFalse(controller.isSaved());
                assertFalse(view.stage().isShowing());
                return null;
            });
            assertEquals("Анна", context.users().getById(original.getId()).getName());
        }
    }

    @Test
    void linkedRoleErrorKeepsFormOpenAndLeavesTheStoredUserUnchanged() throws Exception {
        AppContext context = new AppContext(new InMemoryUserRepository(), new InMemoryOrderRepository());
        User user = context.users().create("Анна", "anna@example.org", UserRole.CUSTOMER);
        context.orders().create("Заказ", "", OrderCategory.DESIGN, BigDecimal.TEN,
                LocalDate.now().plusDays(1), user.getId());
        try (View view = open(context, "user-form.fxml")) {
            onFx(() -> {
                UserFormController controller = view.loader().getController();
                controller.setUser(user);
                view.field("nameField").setText("Другое имя");
                @SuppressWarnings("unchecked")
                ComboBox<UserRole> roles = (ComboBox<UserRole>) view.root().lookup("#roleBox");
                roles.setValue(UserRole.FREELANCER);
                view.button("saveButton").fire();
                return null;
            });
            finishTasks(context);
            onFx(() -> {
                assertTrue(view.stage().isShowing());
                assertTrue(((Label) view.root().lookup("#errorLabel")).getText().contains("заказчиком"));
                assertEquals("Другое имя", view.field("nameField").getText());
                assertFalse(view.button("saveButton").isDisabled());
                return null;
            });
            assertEquals("Анна", context.users().getById(user.getId()).getName());
            assertEquals(UserRole.CUSTOMER, context.users().getById(user.getId()).getRole());
        }
    }

    @Test
    void startupLoadsUsersAndRefreshesStatisticsWhenTheTabChanges() throws Exception {
        AppContext context = new AppContext(new InMemoryUserRepository(), new InMemoryOrderRepository());
        context.users().create("Анна", "anna@example.org", UserRole.CUSTOMER);
        try (View view = open(context, "startup.fxml")) {
            finishTasks(context);
            onFx(() -> {
                Parent usersRoot = (Parent) view.loader().getNamespace().get("usersView");
                assertEquals(1, ((TableView<?>) usersRoot.lookup("#table")).getItems().size());
                ((TabPane) view.root()).getSelectionModel().select(1);
                return null;
            });
            finishTasks(context);
            onFx(() -> {
                Parent statsRoot = (Parent) view.loader().getNamespace().get("statisticsView");
                assertEquals(13, ((TableView<?>) statsRoot.lookup("#table")).getItems().size());
                return null;
            });
        }
    }

    @Test
    void modalCannotCloseDuringSaveAndParentReloadsAfterwards() throws Exception {
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        InMemoryUserRepository users = new InMemoryUserRepository() {
            @Override
            public User save(User user) {
                writing.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Сохранение не разблокировано");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                return super.save(user);
            }
        };
        try (View view = open(new AppContext(users, new InMemoryOrderRepository()), "users.fxml")) {
            Platform.runLater(() -> view.button("addButton").fire());
            Stage modal = onFx(() -> (Stage) Window.getWindows().stream()
                    .filter(window -> window instanceof Stage child && child.getOwner() == view.stage()).findFirst().orElseThrow());
            onFx(() -> {
                Parent root = modal.getScene().getRoot();
                ((TextField) root.lookup("#nameField")).setText("Иван");
                ((TextField) root.lookup("#emailField")).setText("ivan@example.org");
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
                    assertTrue(modal.getScene().getRoot().lookup("#nameField").isDisabled());
                    return null;
                });
            } finally {
                release.countDown();
            }
            finishTasks(view.context());
            finishTasks(view.context());
            onFx(() -> {
                assertFalse(modal.isShowing());
                assertEquals(1, ((TableView<?>) view.root().lookup("#table")).getItems().size());
                return null;
            });
        }
    }
}
