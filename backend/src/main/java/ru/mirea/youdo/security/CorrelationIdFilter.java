package ru.mirea.youdo.security;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-ID";
    public static String current() { return MDC.get("correlationId"); }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String value = request.getHeader(HEADER);
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) value = UUID.randomUUID().toString();
        request.setAttribute(HEADER, value);
        response.setHeader(HEADER, value);
        MDC.put("correlationId", value);
        try { chain.doFilter(request, response); } finally { MDC.remove("correlationId"); }
    }
}
