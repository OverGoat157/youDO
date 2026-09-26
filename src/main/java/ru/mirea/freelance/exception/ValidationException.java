package ru.mirea.freelance.exception;

/** Некорректные входные данные. */
public class ValidationException extends BusinessException {
    public ValidationException(String message) {
        super(message);
    }
}
