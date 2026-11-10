package ru.mirea.youdo.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.mirea.youdo.error.ApiException;
import ru.mirea.youdo.security.NewPasswordPolicy;

@Configuration
public class AuthBeans {
    @Bean public Clock clock() { return Clock.systemUTC(); }
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean @ConditionalOnMissingBean(NewPasswordPolicy.class)
    public NewPasswordPolicy baselinePasswordPolicy() {
        return password -> {
            int bytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
            if (bytes < 12 || bytes > 72 || password.codePoints().noneMatch(Character::isUpperCase)
                    || password.codePoints().noneMatch(Character::isLowerCase)
                    || password.codePoints().noneMatch(Character::isDigit)
                    || password.codePoints().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
                throw new ApiException(400, "PASSWORD_POLICY", "Пароль: 12–72 байта UTF-8, верхний и нижний регистр, цифра и спецсимвол");
            }
        };
    }
}
