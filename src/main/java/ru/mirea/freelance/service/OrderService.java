package ru.mirea.freelance.service;

import ru.mirea.freelance.exception.BusinessException;
import ru.mirea.freelance.exception.EntityNotFoundException;
import ru.mirea.freelance.exception.ValidationException;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;
import ru.mirea.freelance.repository.OrderRepository;
import ru.mirea.freelance.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Операции с заказами; хранилище передаётся через интерфейс. */
public class OrderService {
    private final OrderRepository orders;
    private final UserRepository users;

    public OrderService(OrderRepository orders, UserRepository users) {
        this.orders = orders;
        this.users = users;
    }

    public Order create(String title, String description, OrderCategory category, BigDecimal budget,
                        LocalDate deadline, Long customerId) {
        validate(title, budget, deadline);
        validateCategory(category);
        User customer = getUser(customerId);
        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new BusinessException("Пользователь с ID " + customerId + " не является заказчиком");
        }
        return orders.save(new Order(title.trim(), description, category, budget, deadline, customerId));
    }

    public List<Order> findAll() {
        return orders.findAll();
    }

    public Order getById(Long id) {
        if (id == null) {
            throw new EntityNotFoundException("Заказ", id);
        }
        return orders.findById(id).orElseThrow(() -> new EntityNotFoundException("Заказ", id));
    }

    public Order update(Long id, String title, String description, OrderCategory category, BigDecimal budget,
                        LocalDate deadline) {
        Order order = getById(id);
        validate(title, budget, deadline);
        validateCategory(category);
        order.setTitle(title.trim());
        order.setDescription(description);
        order.setCategory(category);
        order.setBudget(budget);
        order.setDeadline(deadline);
        orders.update(order);
        return order;
    }

    public void delete(Long id) {
        getById(id);
        orders.deleteById(id);
    }

    private User getUser(Long id) {
        if (id == null) {
            throw new EntityNotFoundException("Пользователь", id);
        }
        return users.findById(id).orElseThrow(() -> new EntityNotFoundException("Пользователь", id));
    }

    private void validate(String title, BigDecimal budget, LocalDate deadline) {
        if (title == null || title.trim().isEmpty()) {
            throw new ValidationException("Название заказа не может быть пустым: " + title);
        }
        if (budget == null || budget.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Бюджет должен быть больше нуля: " + budget);
        }
        if (deadline == null) {
            throw new ValidationException("Дедлайн не задан: " + deadline);
        }
        if (deadline.isBefore(LocalDate.now())) {
            throw new ValidationException("Дедлайн не может быть в прошлом: " + deadline);
        }
    }

    private void validateCategory(OrderCategory category) {
        if (category == null) {
            throw new ValidationException("Недопустимая категория: " + category);
        }
    }
}
