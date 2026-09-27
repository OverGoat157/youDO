package ru.mirea.freelance.exception;

/** Нарушение правил работы биржи. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
