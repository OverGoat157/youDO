package ru.mirea.freelance.util;

import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.User;

import java.nio.file.Path;
import java.util.List;

/**
 * Экспорт пользователей и заказов в файлы.
 * Меню работает только с этим интерфейсом и не знает, какой формат внутри:
 * новый формат (например, JSON) — это новый класс, меню не меняется.
 */
public interface Exporter {

    /**
     * Сохраняет данные в папку directory (создаёт её, если нет).
     * Возвращает список созданных файлов: у Excel один, у CSV два.
     */
    List<Path> export(List<User> users, List<Order> orders, Path directory);
}
