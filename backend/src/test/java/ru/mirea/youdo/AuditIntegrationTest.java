package ru.mirea.youdo;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import ru.mirea.youdo.controller.AuditController;
import ru.mirea.youdo.domain.AuditEvent;
import ru.mirea.youdo.error.ApiExceptionHandler;
import ru.mirea.youdo.error.ErrorResponses;
import ru.mirea.youdo.repository.AuditEventRepository;
import ru.mirea.youdo.security.CorrelationIdFilter;
import ru.mirea.youdo.service.AuditQueryService;
import java.time.Instant;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=AuditIntegrationTest.Application.class, properties={
    "spring.datasource.url=jdbc:h2:mem:audit;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false", "spring.flyway.enabled=false"})
@AutoConfigureMockMvc
class AuditIntegrationTest {
    @Configuration @EnableAutoConfiguration @EnableMethodSecurity
    @EntityScan("ru.mirea.youdo.domain") @EnableJpaRepositories("ru.mirea.youdo.repository")
    @Import({AuditQueryService.class, AuditController.class, ApiExceptionHandler.class,
        ErrorResponses.class, CorrelationIdFilter.class})
    static class Application {
        @Bean SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(a -> a.anyRequest().authenticated()).httpBasic(c -> {}).build();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired AuditEventRepository events;
    static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");
    static final UUID ORDER = UUID.randomUUID();

    @BeforeEach void seed() {
        events.deleteAll();
        events.save(new AuditEvent(UUID.randomUUID(), "admin@youdo.local", "LOGIN", "ACCOUNT", null, T0, "Вход выполнен", "corr-1"));
        events.save(new AuditEvent(null, null, "LOGIN_FAILED", "ACCOUNT", null, T0.plusSeconds(60), "Вход отклонён", "corr-2"));
        events.save(new AuditEvent(UUID.randomUUID(), "editor@youdo.local", "ORDER_UPDATED", "ORDER", ORDER,
            T0.plusSeconds(120), "Скидка 50% на заказ", "corr-3"));
        events.save(new AuditEvent(UUID.randomUUID(), "admin@youdo.local", "CATEGORY_CREATED", "CATEGORY", null,
            T0.plusSeconds(180), "Категория создана", "corr-4"));
    }

    @Test void onlyAdminReadsAudit() throws Exception {
        mvc.perform(get("/api/admin/audit")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/audit").with(user("v").roles("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit").with(user("e").roles("EDITOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit").with(user("a").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(4));
    }

    @Test void newestFirstByDefaultAndSortAscending() throws Exception {
        mvc.perform(get("/api/admin/audit").with(user("a").roles("ADMIN")))
            .andExpect(jsonPath("content[0].action").value("CATEGORY_CREATED"))
            .andExpect(jsonPath("content[3].action").value("LOGIN"));
        mvc.perform(get("/api/admin/audit?sort=occurredAt,asc").with(user("a").roles("ADMIN")))
            .andExpect(jsonPath("content[0].action").value("LOGIN"));
        mvc.perform(get("/api/admin/audit?sort=action,asc").with(user("a").roles("ADMIN")))
            .andExpect(jsonPath("content[*].action", contains("CATEGORY_CREATED", "LOGIN", "LOGIN_FAILED", "ORDER_UPDATED")));
    }

    @Test void freeTextSearchIgnoresCaseAndTreatsPercentLiterally() throws Exception {
        mvc.perform(get("/api/admin/audit").param("q", "EDITOR@").with(user("a").roles("ADMIN")))
            .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].entityId").value(ORDER.toString()));
        mvc.perform(get("/api/admin/audit").param("q", "вход").with(user("a").roles("ADMIN")))
            .andExpect(jsonPath("totalElements").value(2));
        mvc.perform(get("/api/admin/audit").param("q", "corr-4").with(user("a").roles("ADMIN")))
            .andExpect(jsonPath("totalElements").value(1));
        // «%» — обычный символ: находит только описание, где он реально есть
        mvc.perform(get("/api/admin/audit").param("q", "%").with(user("a").roles("ADMIN")))
            .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].action").value("ORDER_UPDATED"));
    }

    @Test void filtersByActionEntityAndTime() throws Exception {
        mvc.perform(get("/api/admin/audit").param("action", "login_failed").with(user("a").roles("ADMIN")))
            .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].actorLogin").value(nullValue()));
        mvc.perform(get("/api/admin/audit").param("entityType", "ACCOUNT").with(user("a").roles("ADMIN")))
            .andExpect(jsonPath("totalElements").value(2));
        mvc.perform(get("/api/admin/audit").param("entityId", ORDER.toString()).with(user("a").roles("ADMIN")))
            .andExpect(jsonPath("totalElements").value(1));
        mvc.perform(get("/api/admin/audit").param("from", "2026-11-20T10:01:00Z").param("to", "2026-11-20T10:03:00Z")
                .with(user("a").roles("ADMIN")))
            .andExpect(jsonPath("content[*].action", contains("ORDER_UPDATED", "LOGIN_FAILED")));
    }

    @Test void paginatesAndRejectsBadParameters() throws Exception {
        mvc.perform(get("/api/admin/audit?page=1&size=3").with(user("a").roles("ADMIN")))
            .andExpect(jsonPath("totalElements").value(4)).andExpect(jsonPath("totalPages").value(2))
            .andExpect(jsonPath("content", hasSize(1))).andExpect(jsonPath("content[0].action").value("LOGIN"));
        for (String bad : new String[]{"size=101", "size=0", "page=-1", "sort=summary,asc", "sort=occurredAt,up",
                "entityId=not-a-uuid", "from=2026-11-21T00:00:00Z&to=2026-11-20T00:00:00Z", "from=yesterday"}) {
            mvc.perform(get("/api/admin/audit?" + bad).with(user("a").roles("ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").exists())
                .andExpect(jsonPath("correlationId").exists());
        }
    }
}
