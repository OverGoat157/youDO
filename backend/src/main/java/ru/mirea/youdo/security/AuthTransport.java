package ru.mirea.youdo.security;

import jakarta.servlet.http.*;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import ru.mirea.youdo.config.YoudoProperties;
import ru.mirea.youdo.dto.AuthDtos.ClientType;
import ru.mirea.youdo.error.ApiException;

@Component
public class AuthTransport {
    public static final String COOKIE="youdo_refresh";
    private final YoudoProperties properties;
    public AuthTransport(YoudoProperties properties) { this.properties=properties; }
    /** Strict Origin validation is the CSRF protection for all browser auth mutations, including login. */
    public void validate(HttpServletRequest request, ClientType type) {
        String origin=request.getHeader("Origin");
        boolean hasCookie=cookie(request) != null;
        boolean allowed=origin != null && properties.allowedOrigins().contains(origin);
        if ((origin != null && !allowed) || (type == ClientType.WEB && !allowed)
                || (type == ClientType.ANDROID && (origin != null || hasCookie)))
            throw new ApiException(403,"ORIGIN_REJECTED","Недопустимый источник запроса");
    }
    public String refresh(HttpServletRequest request, ClientType type, String bodyToken) {
        validate(request,type);
        if (type == ClientType.WEB && bodyToken != null)
            throw new ApiException(400,"INVALID_REQUEST","WEB передаёт refresh только в cookie");
        return type == ClientType.WEB ? cookie(request) : bodyToken;
    }
    public void setCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(COOKIE,token == null ? "" : token)
            .httpOnly(true).secure(properties.cookieSecure()).sameSite("Strict").path("/api/auth")
            .maxAge(token == null ? Duration.ZERO : properties.refreshDuration()).build().toString());
    }
    private String cookie(HttpServletRequest r) {
        return r.getCookies() == null ? null : Arrays.stream(r.getCookies()).filter(c -> COOKIE.equals(c.getName()))
            .map(Cookie::getValue).findFirst().orElse(null);
    }
}
