package ru.mirea.youdo.config;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.mirea.youdo.error.ApiException;
import ru.mirea.youdo.security.NewPasswordPolicy;
import ru.mirea.youdo.service.AccountProblem;
import ru.mirea.youdo.service.PasswordPolicy;

@Configuration
public class AuthBeans {
    @Bean public Clock clock() { return Clock.systemUTC(); }
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean
    public NewPasswordPolicy newPasswordPolicy(PasswordPolicy policy) {
        return password -> {
            try {
                policy.validate(password);
            } catch (AccountProblem e) {
                throw new ApiException(400, "PASSWORD_POLICY", e.getMessage());
            }
        };
    }
}
