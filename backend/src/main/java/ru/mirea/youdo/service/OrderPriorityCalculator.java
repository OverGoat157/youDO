package ru.mirea.youdo.service;
import java.time.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Component;
import ru.mirea.youdo.domain.*;
@Component
public class OrderPriorityCalculator {
    public static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private final Clock clock;
    public OrderPriorityCalculator(Clock clock) { this.clock=clock; }
    public LocalDate today() { return LocalDate.now(clock.withZone(ZONE)); }
    public OrderPriority calculate(OrderStatus status, boolean urgent, LocalDate deadline, LocalDate today) {
        if (status.isFinal()) return OrderPriority.LOW;
        if (urgent || deadline.isBefore(today)) return OrderPriority.HIGH;
        return deadline.isAfter(today.plusDays(3)) ? OrderPriority.LOW : OrderPriority.MEDIUM;
    }
    /** Reusable CASE for filters and DB GROUP BY in AnalyticsService. Pass one date per operation. */
    public Expression<String> expression(Root<Order> root, CriteriaBuilder cb, LocalDate today) {
        return cb.<String>selectCase()
            .when(root.get("status").in(OrderStatus.COMPLETED,OrderStatus.CANCELLED),OrderPriority.LOW.name())
            .when(cb.or(cb.isTrue(root.get("urgent")),cb.lessThan(root.get("deadline"),today)),OrderPriority.HIGH.name())
            .when(cb.lessThanOrEqualTo(root.get("deadline"),today.plusDays(3)),OrderPriority.MEDIUM.name())
            .otherwise(OrderPriority.LOW.name());
    }
}
