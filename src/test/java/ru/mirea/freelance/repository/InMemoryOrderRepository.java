package ru.mirea.freelance.repository;

import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Хранилище заказов для тестов бизнес-правил без JDBC. */
public class InMemoryOrderRepository implements OrderRepository {
    private final Map<Long, Order> store = new HashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public Order save(Order order) {
        order.setId(seq.incrementAndGet());
        store.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Order> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void update(Order order) {
        store.replace(order.getId(), order);
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public List<Order> findByTitleContaining(String part) {
        return store.values().stream()
                .filter(order -> order.getTitle().toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT)))
                .toList();
    }

    @Override
    public List<Order> findByCustomerId(Long customerId) {
        return store.values().stream().filter(order -> Objects.equals(order.getCustomerId(), customerId)).toList();
    }

    @Override
    public List<Order> findByFreelancerId(Long freelancerId) {
        return store.values().stream().filter(order -> Objects.equals(order.getFreelancerId(), freelancerId)).toList();
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return store.values().stream().filter(order -> order.getStatus() == status).toList();
    }

    @Override
    public long countActiveByUserId(Long userId) {
        return store.values().stream().filter(order -> !order.getStatus().isFinal())
                .filter(order -> Objects.equals(order.getCustomerId(), userId)
                        || Objects.equals(order.getFreelancerId(), userId)).count();
    }
}
