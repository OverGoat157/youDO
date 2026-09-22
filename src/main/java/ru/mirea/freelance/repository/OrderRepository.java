package ru.mirea.freelance.repository;

import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderStatus;

import java.util.List;

/** Хранилище заказов. Реализации: JDBC в приложении, in-memory в тестах. */
public interface OrderRepository extends Repository<Order, Long> {

    List<Order> findByTitleContaining(String part);

    List<Order> findByCustomerId(Long customerId);

    List<Order> findByFreelancerId(Long freelancerId);

    List<Order> findByStatus(OrderStatus status);

    /** Заказы в статусах OPEN, IN_PROGRESS, ON_REVIEW, где пользователь заказчик или исполнитель. Для правила 7. */
    long countActiveByUserId(Long userId);
}