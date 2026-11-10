package ru.mirea.youdo.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.mirea.youdo.error.ErrorResponses;

public class AccessTokenFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final AccountCredentialsStore accounts;
    private final ErrorResponses errors;
    public AccessTokenFilter(TokenService tokens, AccountCredentialsStore accounts, ErrorResponses errors) {
        this.tokens=tokens; this.accounts=accounts; this.errors=errors;
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        // Refresh/logout can recover or close a session even if a caller sends an expired access token.
        String path=req.getRequestURI().substring(req.getContextPath().length());
        boolean anonymousAuth="POST".equals(req.getMethod()) && Set.of("/api/auth/login","/api/auth/refresh","/api/auth/logout").contains(path);
        String header=req.getHeader("Authorization");
        if (header != null && !anonymousAuth) {
            AccountCredentials account;
            try {
                if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
                var claims=tokens.parse(header.substring(7));
                UUID id=UUID.fromString(claims.getSubject());
                Number cv=claims.get("cv", Number.class);
                account=accounts.findById(id).orElseThrow(IllegalArgumentException::new);
                if (!account.active() || cv == null || cv.longValue() != account.credentialsVersion()) throw new IllegalArgumentException();
            } catch (JwtException | IllegalArgumentException e) {
                errors.write(req,res,401,"UNAUTHORIZED","Требуется действующая авторизация"); return;
            }
            var roles=new HashSet<>(account.roles());
            if (roles.contains("ADMIN")) roles.add("EDITOR");
            if (roles.contains("EDITOR")) roles.add("VIEWER");
            var authorities=roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_"+r)).toList();
            var principal=CurrentUser.from(account);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,authorities));
            boolean permitted=("GET".equals(req.getMethod()) && "/api/auth/me".equals(path))
                || ("POST".equals(req.getMethod()) && "/api/auth/change-password".equals(path));
            if (principal.mustChangePassword() && !permitted) {
                errors.write(req,res,403,"PASSWORD_CHANGE_REQUIRED","Сначала смените временный пароль"); return;
            }
        }
        chain.doFilter(req,res);
    }
}
