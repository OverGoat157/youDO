package ru.mirea.youdo.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.youdo.domain.*;
import ru.mirea.youdo.dto.AnalyticsDtos.*;
import ru.mirea.youdo.repository.OrderRepository;

/** Сводка по заказам. Все подсчёты выполняет база (COUNT, SUM, GROUP BY), заказы в память не загружаются. */
@Service
public class AnalyticsService {
    private static final Specification<Order> ACTIVE = (o, q, cb) -> cb.isFalse(o.get("archived"));

    private final OrderRepository orders;
    private final EntityManager entities;
    private final OrderPriorityCalculator priority;

    public AnalyticsService(OrderRepository orders, EntityManager entities, OrderPriorityCalculator priority) {
        this.orders = orders; this.entities = entities; this.priority = priority;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
    public Summary summary() {
        LocalDate today = priority.today(); // одна дата на всю сводку, как требует контракт priority
        long total = orders.count();
        long active = orders.count(ACTIVE);
        long urgent = orders.count(ACTIVE.and((o, q, cb) -> cb.isTrue(o.get("urgent"))));
        return new Summary(total, active, total - active, urgent, averageBudget(), byCategory(), byStatus(),
            byPriority(today), today);
    }

    /** SUM/COUNT в BigDecimal, а не AVG: AVG вернул бы double и мог потерять копейки. */
    private BigDecimal averageBudget() {
        CriteriaBuilder cb = entities.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Order> o = q.from(Order.class);
        q.multiselect(cb.sum(o.<BigDecimal>get("budget")), cb.count(o)).where(cb.isFalse(o.get("archived")));
        Tuple row = entities.createQuery(q).getSingleResult();
        BigDecimal sum = row.get(0, BigDecimal.class);
        long count = row.get(1, Long.class);
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    /** GROUP BY категории; категории без активных заказов не попадают. Сначала самые крупные. */
    private List<Group> byCategory() {
        CriteriaBuilder cb = entities.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Order> o = q.from(Order.class);
        Join<Order, Category> c = o.join("category");
        q.multiselect(c.get("code"), c.get("name"), cb.count(o)).where(cb.isFalse(o.get("archived")))
            .groupBy(c.get("code"), c.get("name")).orderBy(cb.desc(cb.count(o)), cb.asc(c.get("name")));
        return entities.createQuery(q).getResultList().stream()
            .map(t -> new Group(t.get(0, String.class), t.get(1, String.class), t.get(2, Long.class))).toList();
    }

    /** GROUP BY статуса; статусы без заказов возвращаются с нулём, чтобы список всегда был полным. */
    private List<Group> byStatus() {
        CriteriaBuilder cb = entities.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Order> o = q.from(Order.class);
        q.multiselect(o.get("status"), cb.count(o)).where(cb.isFalse(o.get("archived"))).groupBy(o.get("status"));
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (Tuple t : entities.createQuery(q).getResultList()) counts.put(t.get(0, OrderStatus.class), t.get(1, Long.class));
        return Arrays.stream(OrderStatus.values())
            .map(s -> new Group(s.name(), s.name(), counts.getOrDefault(s, 0L))).toList();
    }

    /**
     * Приоритет не хранится в БД, поэтому считаем его тем же SQL CASE, что и фильтр priority в списке заказов:
     * по одному COUNT на значение. Предикат буквально тот же, поэтому цифры сводки и список с фильтром совпадают.
     */
    private List<Group> byPriority(LocalDate today) {
        return Arrays.stream(OrderPriority.values())
            .map(p -> new Group(p.name(), p.name(), orders.count(ACTIVE.and(
                (o, q, cb) -> cb.equal(priority.expression(o, cb, today), p.name()))))).toList();
    }
}
