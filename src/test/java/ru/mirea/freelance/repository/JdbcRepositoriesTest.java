package ru.mirea.freelance.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.mirea.freelance.exception.DatabaseException;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.OrderStatus;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;
import ru.mirea.freelance.util.DatabaseManager;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Только отдельная подготовленная БД: -Dyoudo.db.tests=true -Dyoudo.test.db.config=/path/db.properties. */
@EnabledIfSystemProperty(named = "youdo.db.tests", matches = "true")
class JdbcRepositoriesTest {
    @Test
    void crudMoneyDatesNullableFreelancerAndDatabaseConstraints() {
        DatabaseManager database = new DatabaseManager(Path.of(System.getProperty("youdo.test.db.config")));
        UserRepository users = new JdbcUserRepository(database);
        OrderRepository orders = new JdbcOrderRepository(database);
        String key = UUID.randomUUID().toString();
        User customer = users.save(new User("Тест заказчика", key + "@example.org", UserRole.CUSTOMER));
        User freelancer = null;
        Order order = null;
        try {
            freelancer = users.save(new User("Тест исполнителя", key + "f@example.org", UserRole.FREELANCER));
            String email = customer.getEmail();
            DatabaseException duplicate = assertThrows(DatabaseException.class,
                    () -> users.save(new User("Другой", email, UserRole.CUSTOMER)));
            assertEquals("Пользователь с таким email уже существует.", duplicate.getMessage());

            customer.setName("Обновлённый заказчик");
            users.update(customer);
            assertEquals(customer.getName(), users.findById(customer.getId()).orElseThrow().getName());
            assertEquals(customer.getId(), users.findByEmail(email).orElseThrow().getId());
            assertNotNull(customer.getRegisteredAt());

            LocalDate deadline = LocalDate.now().plusDays(20);
            order = orders.save(new Order(key, "Описание", OrderCategory.DEVELOPMENT,
                    new BigDecimal("1234.56"), deadline, customer.getId()));
            Order restored = orders.findById(order.getId()).orElseThrow();
            assertNull(restored.getFreelancerId());
            assertEquals(new BigDecimal("1234.56"), restored.getBudget());
            assertEquals(deadline, restored.getDeadline());
            assertNotNull(restored.getCreatedAt());
            assertEquals(order.getId(), orders.findByTitleContaining(key.toUpperCase()).getFirst().getId());
            assertEquals(1, orders.countActiveByUserId(customer.getId()));

            order.setFreelancerId(freelancer.getId());
            order.setStatus(OrderStatus.IN_PROGRESS);
            orders.update(order);
            assertEquals(freelancer.getId(), orders.findById(order.getId()).orElseThrow().getFreelancerId());
            assertEquals(1, orders.countActiveByUserId(freelancer.getId()));
            assertTrue(orders.findByFreelancerId(freelancer.getId()).contains(order));
            assertTrue(orders.findByCustomerId(customer.getId()).contains(order));
            assertTrue(orders.findByStatus(OrderStatus.IN_PROGRESS).contains(order));

            order.setStatus(OrderStatus.COMPLETED);
            orders.update(order);
            assertEquals(0, orders.countActiveByUserId(customer.getId()));
            DatabaseException referenced = assertThrows(DatabaseException.class,
                    () -> users.deleteById(customer.getId()));
            assertTrue(referenced.getMessage().contains("в том числе завершённые"));
            assertTrue(users.existsById(customer.getId()));
            // Новый набор репозиториев видит сохранённые данные (соединения не держатся в памяти).
            assertEquals(OrderStatus.COMPLETED,
                    new JdbcOrderRepository(database).findById(order.getId()).orElseThrow().getStatus());
        } finally {
            if (order != null) orders.deleteById(order.getId());
            if (freelancer != null) users.deleteById(freelancer.getId());
            users.deleteById(customer.getId());
        }
        assertFalse(users.existsById(customer.getId()));
    }
}
