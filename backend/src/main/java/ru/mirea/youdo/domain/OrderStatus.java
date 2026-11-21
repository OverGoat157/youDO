package ru.mirea.youdo.domain;
public enum OrderStatus {
    OPEN, IN_PROGRESS, ON_REVIEW, COMPLETED, CANCELLED;
    public boolean isFinal() { return this == COMPLETED || this == CANCELLED; }
    public boolean canTransitionTo(OrderStatus next) {
        if (next == this) return true;
        return switch(this) {
            case OPEN -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == ON_REVIEW || next == CANCELLED;
            case ON_REVIEW -> next == COMPLETED || next == IN_PROGRESS;
            default -> false;
        };
    }
}
