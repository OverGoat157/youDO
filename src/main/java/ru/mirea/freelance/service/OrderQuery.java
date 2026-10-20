package ru.mirea.freelance.service;

import ru.mirea.freelance.model.OrderCategory;
import ru.mirea.freelance.model.OrderStatus;

/** Пустой текст и null-фильтры не ограничивают результат поиска. */
public record OrderQuery(String text, OrderStatus status, OrderCategory category) {
}
