package ru.mirea.freelance.model;

import ru.mirea.freelance.exception.ValidationException;

import java.util.Arrays;
import java.util.Locale;

/** Категория заказа. */
public enum OrderCategory {
    DEVELOPMENT,
    DESIGN,
    COPYWRITING,
    MARKETING,
    OTHER;

    public static OrderCategory fromString(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException("Недопустимое значение: " + s
                    + ". Допустимые: " + Arrays.toString(values()));
        }
    }
}
