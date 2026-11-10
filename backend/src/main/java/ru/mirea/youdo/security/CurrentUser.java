package ru.mirea.youdo.security;

import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.mirea.youdo.error.ApiException;

/** Principal with no password hash. Authorities are read from the current database account. */
public record CurrentUser(UUID id, String name, String login, Set<String> roles,
                          boolean mustChangePassword, long credentialsVersion) {
    public CurrentUser { roles=Set.copyOf(roles); }
    public static CurrentUser from(AccountCredentials a) {
        return new CurrentUser(a.id(), a.name(), a.login(), a.roles(), a.mustChangePassword(), a.credentialsVersion());
    }
    public static Optional<CurrentUser> optional() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof CurrentUser user ? Optional.of(user) : Optional.empty();
    }
    public static CurrentUser require() { return optional().orElseThrow(ApiException::unauthorized); }
}
