package ru.mirea.youdo.config;

import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import ru.mirea.youdo.error.ErrorResponses;
import ru.mirea.youdo.security.*;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http, TokenService tokens,
            AccountCredentialsStore accounts, ErrorResponses errors, YoudoProperties properties) throws Exception {
        return http.cors(c -> {}).csrf(c -> c.disable()) // Cookie auth mutations enforce Origin in AuthTransport.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable()).formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> errors.write(req,res,401,"UNAUTHORIZED","Требуется действующая авторизация"))
                .accessDeniedHandler((req,res,ex) -> errors.write(req,res,403,"FORBIDDEN","Недостаточно прав")))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                .requestMatchers(HttpMethod.GET,"/","/index.html","/favicon.ico","/assets/**","/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/auth/login","/api/auth/refresh","/api/auth/logout").permitAll()
                .requestMatchers("/api/auth/me","/api/auth/change-password").authenticated()
                .requestMatchers("/swagger-ui.html","/swagger-ui/**","/v3/api-docs/**","/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/orders/*/permanent").access((authentication, context) ->
                    new AuthorizationDecision(properties.permanentDeleteEnabled() && authentication.get().getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))))
                .requestMatchers(HttpMethod.POST,"/api/categories","/api/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,"/api/categories","/api/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/categories","/api/categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/**").hasRole("VIEWER")
                .requestMatchers("/api/**").hasRole("EDITOR")
                .anyRequest().denyAll())
            .addFilterBefore(new AccessTokenFilter(tokens,accounts,errors),UsernamePasswordAuthenticationFilter.class)
            .build();
    }
    @Bean CorsConfigurationSource corsConfigurationSource(YoudoProperties properties) {
        var cors=new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins()); cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization","Content-Type","X-Correlation-ID"));
        cors.setExposedHeaders(List.of("X-Correlation-ID","Location","Content-Disposition"));
        var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",cors); return source;
    }
}
