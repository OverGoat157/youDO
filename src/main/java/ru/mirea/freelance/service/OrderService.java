package ru.mirea.freelance.service;

import ru.mirea.freelance.exception.BusinessException;
import ru.mirea.freelance.exception.EntityNotFoundException;
import ru.mirea.freelance.exception.ValidationException;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.OrderStatus;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;
import ru.mirea.freelance.repository.OrderRepository;
import ru.mirea.freelance.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
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

    public Order assignFreelancer(Long orderId, Long freelancerId) {
        Order order = getById(orderId);
        User freelancer = getUser(freelancerId);
        if (freelancerId.equals(order.getCustomerId())) {
            throw new BusinessException("Заказчик не может быть исполнителем своего заказа: пользователь с ID "
                    + freelancerId + ", заказ с ID " + orderId);
        }
        if (freelancer.getRole() != UserRole.FREELANCER) {
            throw new BusinessException("Пользователь с ID " + freelancerId + " не является исполнителем");
        }
        if (order.getStatus().isFinal()) {
            throw new BusinessException("Нельзя назначить исполнителя на заказ с ID " + orderId
                    + " в статусе " + order.getStatus());
        }
        order.setFreelancerId(freelancerId);
        orders.update(order);
        return order;
    }

    public Order changeStatus(Long orderId, OrderStatus next) {
        Order order = getById(orderId);
        OrderStatus current = order.getStatus();
        if (!current.canTransitionTo(next)) {
            throw new BusinessException("Переход " + current + " → " + next + " запрещён");
        }
        if (next == OrderStatus.IN_PROGRESS && order.getFreelancerId() == null) {
            throw new BusinessException("Нельзя взять в работу заказ без исполнителя: заказ с ID " + orderId);
        }
        order.setStatus(next);
        orders.update(order);
        return order;
    }

    public List<Order> searchByTitle(String part) {
        if (part == null) {
            throw new ValidationException("Не задана строка поиска: " + part);
        }
        return orders.findByTitleContaining(part);
    }

    public List<Order> searchByCustomer(Long customerId) {
        if (customerId == null) {
            throw new ValidationException("Не задан ID заказчика: " + customerId);
        }
        return orders.findByCustomerId(customerId);
    }

    public List<Order> searchByFreelancer(Long freelancerId) {
        if (freelancerId == null) {
            throw new ValidationException("Не задан ID исполнителя: " + freelancerId);
        }
        return orders.findByFreelancerId(freelancerId);
    }

    public List<Order> filterByStatus(OrderStatus status) {
        if (status == null) {
            throw new ValidationException("Недопустимый статус: " + status);
        }
        return orders.findAll().stream().filter(order -> order.getStatus() == status).toList();
    }

    public List<Order> filterByCategory(OrderCategory category) {
        validateCategory(category);
        return orders.findAll().stream().filter(order -> order.getCategory() == category).toList();
    }

    public List<Order> filterByBudget(BigDecimal min, BigDecimal max) {
        if (min == null || max == null || min.compareTo(max) > 0) {
            throw new ValidationException("Недопустимый диапазон бюджета: " + min + " — " + max);
        }
        return orders.findAll().stream()
                .filter(order -> order.getBudget().compareTo(min) >= 0 && order.getBudget().compareTo(max) <= 0)
                .toList();
    }

    public List<Order> filterByDeadline(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ValidationException("Недопустимый диапазон дедлайнов: " + from + " — " + to);
        }
        return orders.findAll().stream()
                .filter(order -> !order.getDeadline().isBefore(from) && !order.getDeadline().isAfter(to))
                .toList();
    }

    public List<Order> sortBy(OrderSortField field, boolean ascending) {
        if (field == null) {
            throw new ValidationException("Недопустимое поле сортировки: " + field);
        }
        Comparator<Order> comparator = switch (field) {
            case BUDGET -> Comparator.comparing(Order::getBudget);
            case DEADLINE -> Comparator.comparing(Order::getDeadline);
            case CREATED_AT -> Comparator.comparing(Order::getCreatedAt);
        };
        return orders.findAll().stream().sorted(ascending ? comparator : comparator.reversed()).toList();
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
