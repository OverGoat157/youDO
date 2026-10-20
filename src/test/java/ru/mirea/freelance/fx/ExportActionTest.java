package ru.mirea.freelance.fx;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import ru.mirea.freelance.model.*;
import ru.mirea.freelance.repository.InMemoryOrderRepository;
import ru.mirea.freelance.repository.InMemoryUserRepository;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static ru.mirea.freelance.fx.FxTestSupport.*;

/** Native FileChooser подменён; запись файла и диалог ошибки настоящие. */
@EnabledIfSystemProperty(named = "youdo.fx.tests", matches = "true")
class ExportActionTest {
    @TempDir Path directory;
    @BeforeAll static void initializeToolkit() throws Exception { startToolkit(); }

    private static class Action extends ExportAction {
        File selected;
        int confirmations;
        final CompletableFuture<String> info = new CompletableFuture<>();
        Action(AppContext context) { super(context); }
        @Override File chooseFile(Window owner) { return selected; }
        @Override boolean confirmOverwrite(Window owner, Path file) { confirmations++; return false; }
        @Override void showInfo(Window owner, String message) { info.complete(message); }
    }

    private AppContext context() { return new AppContext(new InMemoryUserRepository(), new InMemoryOrderRepository()); }

    @Test
    void cancelAndRejectedOverwriteDoNotWriteFiles() throws Exception {
        try (AppContext context = context()) {
            Action action = new Action(context);
            onFx(() -> { action.exportOrders(null, List.of(), List.of()); return null; });
            try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
            Path existing = directory.resolve("orders.csv");
            Files.writeString(existing, "Старые данные");
            action.selected = directory.resolve("orders").toFile();
            onFx(() -> { action.exportOrders(null, List.of(), List.of()); return null; });
            assertEquals(1, action.confirmations);
            assertEquals("Старые данные", Files.readString(existing));
            assertFalse(action.info.isDone());
        }
    }

    @Test
    void exportUsesCopiedListAndReportsTheActualFilename() throws Exception {
        try (AppContext context = context()) {
            User customer = context.users().create("Анна", "anna@example.org", UserRole.CUSTOMER);
            Order order = context.orders().create("Один видимый заказ", null, OrderCategory.DESIGN,
                    new BigDecimal("1.25"), LocalDate.now().plusDays(1), customer.getId());
            List<Order> visible = new ArrayList<>(List.of(order));
            Action action = new Action(context);
            action.selected = directory.resolve("visible").toFile();
            onFx(() -> {
                action.exportOrders(null, visible, context.users().findAll());
                visible.clear();
                return null;
            });
            String message = action.info.get(10, TimeUnit.SECONDS);
            Path expected = directory.resolve("visible.csv");
            assertTrue(message.contains(expected.toAbsolutePath().toString()));
            assertTrue(message.contains("сохранено: 1"));
            assertEquals(2, Files.readAllLines(expected).size());
            assertTrue(Files.readString(expected).contains("Один видимый заказ"));
        }
    }

    @Test
    void writeErrorGivesAnExplanationAndAllowsRetry() throws Exception {
        try (AppContext context = context()) {
            Action action = new Action(context);
            Path invalid = Files.createDirectory(directory.resolve("folder.csv"));
            action.selected = invalid.toFile();
            onFx(() -> { action.exportOrders(null, List.of(), List.of()); return null; });
            finishTasks(context);
            onFx(() -> {
                DialogPane dialog = Window.getWindows().stream().map(window -> window.getScene().getRoot())
                        .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElseThrow();
                assertTrue(dialog.getContentText().contains("Не удалось сохранить файл"));
                assertTrue(dialog.getContentText().contains(invalid.toString()));
                ((Button) dialog.lookupButton(ButtonType.OK)).fire();
                return null;
            });
            action.selected = directory.resolve("retry.csv").toFile();
            onFx(() -> { action.exportOrders(null, List.of(), List.of()); return null; });
            assertTrue(action.info.get(10, TimeUnit.SECONDS).contains("retry.csv"));
            assertEquals(1, Files.readAllLines(action.selected.toPath()).size());
        }
    }
}
