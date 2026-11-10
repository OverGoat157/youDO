package ru.mirea.youdo.config;

import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import ru.mirea.youdo.error.ErrorResponses;

@Configuration
public class CorsFilterConfig {
    @Bean CorsFilter corsFilter(@Qualifier("corsConfigurationSource") CorsConfigurationSource source, ErrorResponses errors) {
        var filter=new CorsFilter(source);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override protected void rejectRequest(org.springframework.http.server.ServerHttpResponse response) {
                // Spring Security's default body is plain text; keep the shared JSON format instead.
                response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
            }
            @Override public boolean processRequest(CorsConfiguration configuration, HttpServletRequest request,
                    HttpServletResponse response) throws IOException {
                boolean allowed=super.processRequest(configuration,request,response);
                if (!allowed) errors.write(request,response,403,"ORIGIN_REJECTED","Недопустимый источник запроса");
                return allowed;
            }
        });
        return filter;
    }
    @Bean FilterRegistrationBean<CorsFilter> disableServletCorsRegistration(CorsFilter filter) {
        var registration=new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Runs once, inside Spring Security after correlation ID.
        return registration;
    }
}
