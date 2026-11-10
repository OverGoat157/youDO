package ru.mirea.youdo.security;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import ru.mirea.youdo.error.ApiException;

@Component
public class LoginRateLimiter {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public LoginRateLimiter(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }
    @Transactional(propagation=Propagation.MANDATORY, noRollbackFor=ApiException.class)
    public String check(String login, String ip) {
        String key=TokenService.hash(login + "\n" + ip);
        // Serializes concurrent guesses for the same login/IP, across backend instances as well.
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))", Object.class, key);
        var now=clock.instant();
        jdbc.update("delete from auth_login_attempts where attempt_key=? and updated_at <= ? and (blocked_until is null or blocked_until <= ?)",
            key, java.sql.Timestamp.from(now.minus(Duration.ofMinutes(15))), java.sql.Timestamp.from(now));
        var blocked=jdbc.queryForList("select blocked_until from auth_login_attempts where attempt_key=? and blocked_until > ?", key, java.sql.Timestamp.from(now));
        if (!blocked.isEmpty()) throw new ApiException(429,"LOGIN_RATE_LIMIT","Слишком много попыток входа. Повторите через 15 минут");
        return key;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void failed(String key) {
        var now=java.sql.Timestamp.from(clock.instant());
        var until=java.sql.Timestamp.from(clock.instant().plus(Duration.ofMinutes(15)));
        jdbc.update("""
            insert into auth_login_attempts(attempt_key,failures,updated_at) values (?,1,?)
            on conflict (attempt_key) do update set failures=auth_login_attempts.failures+1,
            updated_at=excluded.updated_at,
            blocked_until=case when auth_login_attempts.failures+1 >= 5 then cast(? as timestamptz) else null end
            """, key, now, until);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void succeeded(String key) { jdbc.update("delete from auth_login_attempts where attempt_key=?", key); }
}
