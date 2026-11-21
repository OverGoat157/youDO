package ru.mirea.youdo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Сводка по заказам для страницы аналитики. */
public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    /** Одна группа: key — код (категории, статуса, приоритета), name — подпись, count — число активных заказов. */
    public record Group(String key, String name, long count) {}

    /**
     * total/active/archived — все заказы; urgent, averageBudget и группы — только активные (archived=false).
     * averageBudget = null, если активных заказов нет. priorityDate — «сегодня», от которого считался приоритет.
     */
    public record Summary(long total, long active, long archived, long urgent, BigDecimal averageBudget,
                          List<Group> byCategory, List<Group> byStatus, List<Group> byPriority, LocalDate priorityDate) {}
}
