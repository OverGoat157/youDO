package ru.mirea.freelance.model;

import ru.mirea.freelance.exception.ValidationException;

import java.util.Arrays;
import java.util.Locale;

/** Роль пользователя: заказчик публикует заказы, фрилансер их выполняет. */
public enum UserRole {
    CUSTOMER,
    FREELANCER;

    public static UserRole fromString(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException("Недопустимое значение: " + s
                    + ". Допустимые: " + Arrays.toString(values()));
        }
    }
}
