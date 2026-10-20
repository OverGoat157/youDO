package ru.mirea.freelance.fx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.mirea.freelance.fx.controller.StatisticsController;
import ru.mirea.freelance.repository.InMemoryOrderRepository;
import ru.mirea.freelance.repository.InMemoryUserRepository;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Запуск с дисплеем: xvfb-run -a mvn -Dyoudo.fx.tests=true test. */
@EnabledIfSystemProperty(named = "youdo.fx.tests", matches = "true")
class FxInfrastructureTest {
    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.countDown();
        });
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    @Test
    void backgroundWorkAndBothCallbacksUseTheRightThreads() throws Exception {
        try (FxTasks tasks = new FxTasks()) {
            CompletableFuture<Boolean> success = new CompletableFuture<>();
            tasks.run(() -> !Platform.isFxApplicationThread(),
                    background -> success.complete(background && Platform.isFxApplicationThread()),
                    success::completeExceptionally);
            assertTrue(success.get(5, TimeUnit.SECONDS));

            IOException expected = new IOException("Проверка ошибки");
            CompletableFuture<Boolean> failure = new CompletableFuture<>();
            tasks.run(() -> { throw expected; },
                    ignored -> failure.complete(false),
                    error -> failure.complete(error == expected && Platform.isFxApplicationThread()));
            assertTrue(failure.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void closeInterruptsWorkAndSuppressesCallbacks() throws Exception {
        FxTasks tasks = new FxTasks();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean callback = new AtomicBoolean();
        try {
            tasks.run(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException error) {
                    interrupted.countDown();
                    throw error;
                }
                return null;
            }, ignored -> callback.set(true), error -> callback.set(true));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            tasks.close();
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            onFx(() -> null);
            assertFalse(callback.get());
            assertThrows(IllegalStateException.class, () -> tasks.run(() -> null, x -> {}, e -> {}));
        } finally {
            tasks.close();
        }
    }

    @Test
    void realFxmlIncludeGetsContextAndRefreshesStatistics() throws Exception {
        try (AppContext context = new AppContext(new InMemoryUserRepository(), new InMemoryOrderRepository())) {
            FXMLLoader loader = onFx(() -> context.load("/ru/mirea/freelance/fx/startup.fxml"));
            StatisticsController controller = (StatisticsController)
                    loader.getNamespace().get("statisticsViewController");
            assertNotNull(controller);
            onFx(() -> { controller.refresh(); return null; });
            // Executor последовательный: маркер приходит после обновления экрана.
            CompletableFuture<Void> finished = new CompletableFuture<>();
            context.tasks().run(() -> null, ignored -> finished.complete(null), finished::completeExceptionally);
            finished.get(5, TimeUnit.SECONDS);
            int count = onFx(() -> {
                Parent root = loader.getRoot();
                TableView<?> table = (TableView<?>) root.lookup("#table");
                return table.getItems().size();
            });
            assertEquals(13, count);
        }
    }
}
