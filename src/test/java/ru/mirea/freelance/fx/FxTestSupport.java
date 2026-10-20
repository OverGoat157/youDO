package ru.mirea.freelance.fx;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Один JavaFX toolkit на весь набор тестов, без задержек через sleep. */
final class FxTestSupport {
    private FxTestSupport() { }

    static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Runnable started = () -> {
            Platform.setImplicitExit(false);
            ready.countDown();
        };
        try {
            Platform.startup(started);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started);
        }
        if (!ready.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX не запустился");
        }
    }

    static <T> T onFx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    static void finishTasks(AppContext context) throws Exception {
        CompletableFuture<Void> finished = new CompletableFuture<>();
        context.tasks().run(() -> null, ignored -> finished.complete(null), finished::completeExceptionally);
        finished.get(10, TimeUnit.SECONDS);
    }
}
