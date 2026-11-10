package ru.mirea.youdo.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("youdo")
public record YoudoProperties(String jwtSecret, String issuer, Duration accessDuration,
        Duration refreshDuration, boolean cookieSecure, List<String> allowedOrigins,
        boolean permanentDeleteEnabled, Bootstrap bootstrap) {
    public record Bootstrap(String login, String name, String password) {
        @Override public String toString() { return "Bootstrap[redacted]"; }
    }
    @Override public String toString() { return "YoudoProperties[redacted]"; }
}
