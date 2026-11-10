package ru.mirea.youdo.config;

import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.mirea.youdo.security.*;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final YoudoProperties properties;
    private final AccountCredentialsStore accounts;
    private final PasswordEncoder encoder;
    private final NewPasswordPolicy policy;
    public BootstrapAdmin(YoudoProperties properties, AccountCredentialsStore accounts, PasswordEncoder encoder, NewPasswordPolicy policy) {
        this.properties = properties; this.accounts = accounts; this.encoder = encoder; this.policy = policy;
    }
    @Override public void run(ApplicationArguments args) {
        var b = properties.bootstrap();
        if (b.login().isBlank() && b.password().isBlank()) return;
        String login = b.login().strip().toLowerCase(Locale.ROOT);
        if (login.length() > 150 || !login.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || b.name().isBlank() || b.name().length() > 100)
            throw new IllegalStateException("Invalid bootstrap account configuration");
        if (accounts.findByLogin(login).isPresent()) return; // Restarts never reset an existing password.
        policy.validate(b.password());
        accounts.bootstrap(login, b.name(), encoder.encode(b.password()));
    }
}
