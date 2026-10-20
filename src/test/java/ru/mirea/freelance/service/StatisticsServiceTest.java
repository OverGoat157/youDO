package ru.mirea.freelance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mirea.freelance.model.Order;
import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.OrderStatus;
import ru.mirea.freelance.model.User;
import ru.mirea.freelance.model.UserRole;
import ru.mirea.freelance.repository.InMemoryOrderRepository;
import ru.mirea.freelance.repository.InMemoryUserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StatisticsServiceTest {

    private InMemoryUserRepository users;
    private InMemoryOrderRepository orders;
    private StatisticsService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        orders = new InMemoryOrderRepository();
        service = new StatisticsService(users, orders);
    }

    private void addOrder(String budget, OrderCategory category, OrderStatus status, LocalDate deadline) {
        orders.save(new Order(null, "Заказ", null, category, new BigDecimal(budget), deadline, status,
                1L, null, LocalDateTime.now()));
    }

    @Test
    void collect_emptyDatabase_zerosAndDash() {
        Map<String, Object> stats = service.collect();

        assertEquals(0, ((Number) stats.get("Всего пользователей")).intValue());
        assertEquals(0, ((Number) stats.get("Всего заказов")).intValue());
        assertEquals(0L, stats.get("Заказов OPEN"));
        assertEquals(new BigDecimal("0.00"), stats.get("Средний бюджет"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) stats.get("Сумма бюджетов активных заказов")));
        assertEquals(0L, stats.get("Просроченных заказов"));
        assertEquals("—", stats.get("Самая популярная категория"));
    }

    @Test
    void collect_keysInStableOrder() {
        assertEquals(List.of("Всего пользователей", "Заказчиков", "Фрилансеров", "Всего заказов",
                        "Заказов OPEN", "Заказов IN_PROGRESS", "Заказов ON_REVIEW", "Заказов COMPLETED",
                        "Заказов CANCELLED", "Средний бюджет", "Сумма бюджетов активных заказов",
                        "Просроченных заказов", "Самая популярная категория"),
                List.copyOf(service.collect().keySet()));
    }

    @Test
    void collect_knownSample_countsByRoleAndStatus() {
        users.save(new User("Заказчик", "c@mail.ru", UserRole.CUSTOMER));
        users.save(new User("Фрилансер 1", "f1@mail.ru", UserRole.FREELANCER));
        users.save(new User("Фрилансер 2", "f2@mail.ru", UserRole.FREELANCER));
        LocalDate future = LocalDate.now().plusDays(10);
        addOrder("100", OrderCategory.DESIGN, OrderStatus.OPEN, future);
        addOrder("200", OrderCategory.DESIGN, OrderStatus.OPEN, future);
        addOrder("300", OrderCategory.DEVELOPMENT, OrderStatus.COMPLETED, future);

        Map<String, Object> stats = service.collect();

        assertEquals(3, ((Number) stats.get("Всего пользователей")).intValue());
        assertEquals(1L, stats.get("Заказчиков"));
        assertEquals(2L, stats.get("Фрилансеров"));
        assertEquals(3, ((Number) stats.get("Всего заказов")).intValue());
        assertEquals(2L, stats.get("Заказов OPEN"));
        assertEquals(1L, stats.get("Заказов COMPLETED"));
        // Статус без заказов всё равно есть в результате — с нулём
        assertEquals(0L, stats.get("Заказов CANCELLED"));
        assertEquals("DESIGN (2)", stats.get("Самая популярная категория"));
    }

    @Test
    void collect_averageBudget_twoDecimals() {
        addOrder("100", OrderCategory.OTHER, OrderStatus.OPEN, LocalDate.now().plusDays(1));
        addOrder("100", OrderCategory.OTHER, OrderStatus.OPEN, LocalDate.now().plusDays(1));
        addOrder("101", OrderCategory.OTHER, OrderStatus.OPEN, LocalDate.now().plusDays(1));

        // 301 / 3 = 100.333… → 100.33
        assertEquals(new BigDecimal("100.33"), service.collect().get("Средний бюджет"));
    }

    @Test
    void collect_averageRoundsExactHalfKopeckUp() {
        addOrder("0.01", OrderCategory.OTHER, OrderStatus.OPEN, LocalDate.now().plusDays(1));
        addOrder("0.06", OrderCategory.OTHER, OrderStatus.OPEN, LocalDate.now().plusDays(1));
        assertEquals(new BigDecimal("0.04"), service.collect().get("Средний бюджет"));
    }

    @Test
    void collect_activeBudget_exactBigDecimalSumWithoutFinalStatuses() {
        LocalDate future = LocalDate.now().plusDays(1);
        addOrder("0.10", OrderCategory.OTHER, OrderStatus.OPEN, future);
        addOrder("0.20", OrderCategory.OTHER, OrderStatus.IN_PROGRESS, future);
        addOrder("1000", OrderCategory.OTHER, OrderStatus.COMPLETED, future);
        addOrder("2000", OrderCategory.OTHER, OrderStatus.CANCELLED, future);

        // В double 0.1 + 0.2 = 0.30000000000000004; BigDecimal даёт ровно 0.30
        assertEquals(new BigDecimal("0.30"), service.collect().get("Сумма бюджетов активных заказов"));
    }

    @Test
    void collect_overdue_onlyPastDeadlineAndNotFinal() {
        LocalDate past = LocalDate.now().minusDays(1);
        addOrder("100", OrderCategory.OTHER, OrderStatus.OPEN, past);
        addOrder("100", OrderCategory.OTHER, OrderStatus.ON_REVIEW, past);
        addOrder("100", OrderCategory.OTHER, OrderStatus.COMPLETED, past);
        addOrder("100", OrderCategory.OTHER, OrderStatus.OPEN, LocalDate.now());

        assertEquals(2L, service.collect().get("Просроченных заказов"));
    }
}
