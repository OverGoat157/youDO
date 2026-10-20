package ru.mirea.freelance.fx;

import javafx.fxml.FXMLLoader;
import ru.mirea.freelance.repository.JdbcOrderRepository;
import ru.mirea.freelance.repository.JdbcUserRepository;
import ru.mirea.freelance.repository.OrderRepository;
import ru.mirea.freelance.repository.UserRepository;
import ru.mirea.freelance.service.OrderService;
import ru.mirea.freelance.service.StatisticsService;
import ru.mirea.freelance.service.UserService;
import ru.mirea.freelance.util.DatabaseManager;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

/** Один граф зависимостей на приложение; конструкторы не открывают соединений с БД. */
public final class AppContext implements AutoCloseable {
    private final UserService users;
    private final OrderService orders;
    private final StatisticsService statistics;
    private final FxTasks tasks;
    private final ExportAction exportAction;

    public AppContext() {
        this(new DatabaseManager());
    }

    public AppContext(DatabaseManager database) {
        this(new JdbcUserRepository(database), new JdbcOrderRepository(database));
    }

    public AppContext(UserRepository userRepository, OrderRepository orderRepository) {
        users = new UserService(userRepository, orderRepository);
        orders = new OrderService(orderRepository, userRepository);
        statistics = new StatisticsService(userRepository, orderRepository);
        tasks = new FxTasks();
        exportAction = new ExportAction(this);
    }

    public UserService users() { return users; }
    public OrderService orders() { return orders; }
    public StatisticsService statistics() { return statistics; }
    public FxTasks tasks() { return tasks; }
    public ExportAction exportAction() { return exportAction; }

    public FXMLLoader load(String resource) {
        if (resource == null || !resource.startsWith("/")) {
            throw new IllegalArgumentException("Нужен абсолютный путь FXML от корня classpath.");
        }
        URL location = AppContext.class.getResource(resource);
        if (location == null) {
            throw new IllegalArgumentException("Не найден ресурс интерфейса: " + resource);
        }
        FXMLLoader loader = new FXMLLoader(location);
        // FXMLLoader передаёт эту factory также загрузчикам вложенных fx:include.
        loader.setControllerFactory(type -> {
            try {
                return type.getConstructor(AppContext.class).newInstance(this);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException("Не удалось создать контроллер " + type.getSimpleName(), e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Контроллер " + type.getSimpleName()
                        + " должен иметь публичный конструктор (AppContext).", e);
            }
        });
        try {
            loader.load();
            return loader;
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось загрузить интерфейс: " + resource, e);
        }
    }

    @Override
    public void close() {
        tasks.close();
    }
}
