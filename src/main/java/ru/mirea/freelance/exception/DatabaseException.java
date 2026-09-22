package ru.mirea.freelance.exception;

/**
 * Ошибка работы с базой данных: нет подключения, неверный SQL, нарушение ограничения.
 * Оборачивает SQLException, чтобы верхние слои не зависели от JDBC.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}