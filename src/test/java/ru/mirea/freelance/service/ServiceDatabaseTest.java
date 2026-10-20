package ru.mirea.freelance.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.mirea.freelance.exception.BusinessException;
import ru.mirea.freelance.exception.DatabaseException;
import ru.mirea.freelance.exception.ValidationException;
import ru.mirea.freelance.fx.AppContext;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.OrderStatus;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;
import ru.mirea.freelance.util.DatabaseManager;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Отдельная подготовленная БД; тест не пересоздаёт таблицы и удаляет только свои записи. */
@EnabledIfSystemProperty(named = "youdo.db.tests", matches = "true")
class ServiceDatabaseTest {
    @Test
    void searchValidationRolesAndWorkflowWorkThroughJdbc() {
        DatabaseManager database = new DatabaseManager(Path.of(System.getProperty("youdo.test.db.config")));
        try (AppContext context = new AppContext(database)) {
            UserService users = context.users();
            OrderService orders = context.orders();
            String key = UUID.randomUUID().toString();
            User customer = users.create("Тест заказчика", key + "@example.org", UserRole.CUSTOMER);
            User freelancer = null;
            Order order = null;
            try {
                freelancer = users.create("Тест исполнителя", key + "f@example.org", UserRole.FREELANCER);
                order = orders.create("Тест поиска", "Описание " + key, OrderCategory.DESIGN,
                        new BigDecimal("1.2300"), LocalDate.now().plusDays(10), customer.getId());
                long orderId = order.getId();
                long freelancerId = freelancer.getId();
                String freelancerEmail = freelancer.getEmail();
                assertEquals(new BigDecimal("1.23"), orders.getById(orderId).getBudget());
                assertEquals(List.of(order), orders.find(new OrderQuery(key.toUpperCase(),
                        OrderStatus.OPEN, OrderCategory.DESIGN)));
                assertThrows(ValidationException.class, () -> users.update(customer.getId(), "Другое имя",
                        freelancerEmail, UserRole.CUSTOMER));
                assertThrows(ValidationException.class, () -> orders.update(orderId, "Изменённый", "",
                        OrderCategory.DESIGN, new BigDecimal("0.001"), LocalDate.now().plusDays(10)));
                assertEquals("Тест поиска", orders.getById(orderId).getTitle());
                assertThrows(BusinessException.class, () -> users.update(customer.getId(), "Другое имя",
                        customer.getEmail(), UserRole.FREELANCER));
                assertEquals("Тест заказчика", users.getById(customer.getId()).getName());
                assertThrows(BusinessException.class, () -> users.delete(customer.getId()));

                orders.assignFreelancer(orderId, freelancerId);
                orders.changeStatus(orderId, OrderStatus.IN_PROGRESS);
                orders.changeStatus(orderId, OrderStatus.ON_REVIEW);
                orders.changeStatus(orderId, OrderStatus.COMPLETED);
                assertThrows(BusinessException.class, () -> users.update(freelancerId, "Другое имя",
                        freelancerEmail, UserRole.CUSTOMER));
                assertEquals(UserRole.FREELANCER, users.getById(freelancerId).getRole());
                assertTrue(assertThrows(DatabaseException.class, () -> users.delete(customer.getId()))
                        .getMessage().contains("завершённые"));
                assertEquals(customer.getEmail(), users.update(customer.getId(), "Обновлённый",
                        " " + customer.getEmail() + " ", UserRole.CUSTOMER).getEmail());
            } finally {
                if (order != null) orders.delete(order.getId());
                if (freelancer != null) users.delete(freelancer.getId());
                users.delete(customer.getId());
            }
        }
    }
}
