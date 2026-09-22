package ru.mirea.freelance.model;

/**
 * Статус заказа и допустимые переходы между статусами:
 * OPEN → IN_PROGRESS → ON_REVIEW → COMPLETED,
 * из OPEN и IN_PROGRESS можно уйти в CANCELLED,
 * из ON_REVIEW можно вернуть в IN_PROGRESS на доработку.
 */
public enum OrderStatus {
    OPEN,
    IN_PROGRESS,
    ON_REVIEW,
    COMPLETED,
    CANCELLED;

    /** Разрешён ли переход из текущего статуса в next. */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case OPEN        -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == ON_REVIEW   || next == CANCELLED;
            case ON_REVIEW   -> next == IN_PROGRESS || next == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /** Финальный статус: заказ больше не меняется. */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED;
    }
}