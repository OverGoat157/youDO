package ru.mirea.freelance.fx;

import javafx.application.Platform;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Последовательные операции БД и файлов в фоне, результат — в JavaFX Application Thread. */
public final class FxTasks implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(work, "youdo-worker");
        thread.setDaemon(true);
        return thread;
    });

    public <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Objects.requireNonNull(work);
        Objects.requireNonNull(onSuccess);
        Objects.requireNonNull(onError);
        if (closed.get()) {
            throw new IllegalStateException("Фоновые задачи уже остановлены.");
        }
        executor.execute(() -> {
            T result;
            try {
                result = work.call();
            } catch (Exception error) {
                deliver(() -> onError.accept(error));
                return;
            }
            deliver(() -> onSuccess.accept(result));
        });
    }

    private void deliver(Runnable callback) {
        if (!closed.get()) {
            Platform.runLater(() -> {
                if (!closed.get()) {
                    callback.run();
                }
            });
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }
}
