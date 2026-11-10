package ru.mirea.youdo.service;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    public void validate(String password) { validate(password, "newPassword"); }
    public void validate(String password, String field) {
        if (password == null || !StandardCharsets.UTF_8.newEncoder().canEncode(password)) fail(field);
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 12 || bytes > 72 || password.indexOf('\0') >= 0
            || password.codePoints().noneMatch(Character::isUpperCase)
            || password.codePoints().noneMatch(Character::isLowerCase)
            || password.codePoints().noneMatch(Character::isDigit)
            || password.codePoints().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) fail(field);
    }
    private void fail(String field) {
        throw AccountProblem.invalid(field, "Пароль: 12–72 байта UTF-8, верхний и нижний регистр, цифра и спецсимвол");
    }
}
