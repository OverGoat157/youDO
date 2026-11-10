package ru.mirea.youdo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.mirea.youdo.domain.*;
import ru.mirea.youdo.dto.AccountDtos;
import ru.mirea.youdo.repository.*;
import ru.mirea.youdo.service.*;
import ru.mirea.youdo.service.AccountIntegrations.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=AccountsIntegrationTest.Application.class, properties={
    "spring.datasource.url=jdbc:h2:mem:accounts;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
class AccountsIntegrationTest {
    @SpringBootConfiguration @EnableAutoConfiguration @EnableMethodSecurity
    @EntityScan("ru.mirea.youdo.domain") @EnableJpaRepositories("ru.mirea.youdo.repository")
    @ComponentScan(basePackages={"ru.mirea.youdo.service", "ru.mirea.youdo.controller"})
    static class Application {
        @Bean SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            // Real method security + CSRF. JWT/password-change filter is supplied by Denis later.
            return http.authorizeHttpRequests(a -> a.anyRequest().authenticated()).httpBasic(c -> {}).build();
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;
    @Autowired CategoryRepository categories;
    @Autowired AccountService service;
    @Autowired CategoryService categoryService;
    @MockitoBean SessionRevoker sessions;
    @MockitoBean AuditWriter audit;
    static final String PASSWORD = "Temporary-12345!";

    @BeforeEach void clear() { accounts.deleteAll(); categories.deleteAll(); reset(sessions, audit); }
    private String body(Object value) throws Exception { return json.writeValueAsString(value); }
    private UUID create() throws Exception {
        String response = mvc.perform(post("/api/admin/users").with(user("admin").roles("ADMIN")).with(csrf())
            .contentType("application/json").content(body(Map.of("name", "Юра", "login", "YURA@example.org", "roles", List.of("VIEWER"), "temporaryPassword", PASSWORD))))
            .andExpect(status().isCreated()).andExpect(jsonPath("login").value("yura@example.org"))
            .andExpect(jsonPath("mustChangePassword").value(true)).andExpect(jsonPath("temporaryPassword").doesNotExist())
            .andExpect(jsonPath("passwordHash").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(PASSWORD));
        return UUID.fromString(json.readTree(response).get("id").asText());
    }
    @Test void createsHashesPaginatesAndRejectsDuplicates() throws Exception {
        UUID id = create();
        UserAccount account = accounts.findById(id).orElseThrow();
        assertTrue(account.getPasswordHash().startsWith("$2a$12$"));
        assertTrue(service.matchesPassword(account, PASSWORD));
        assertFalse(body(account).contains(account.getPasswordHash()));
        mvc.perform(get("/api/admin/users?page=0&size=1").with(user("a").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(id.toString()));
        mvc.perform(get("/api/admin/users?page=-1").with(user("a").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users?size=101").with(user("a").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/users").with(user("a").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(body(Map.of("name", "Duplicate", "login", "YURA@example.org", "roles", List.of("EDITOR"), "temporaryPassword", PASSWORD))))
            .andExpect(status().isConflict()).andExpect(jsonPath("fieldErrors[0].field").value("login"));
        verify(audit).record("ACCOUNT_CREATED", "ACCOUNT", id, "Создан аккаунт");
    }
    @Test void updateUnchangedLoginResetAndDisableInvalidateCredentials() throws Exception {
        UUID id = create();
        mvc.perform(put("/api/admin/users/"+id).with(user("a").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(body(Map.of("name", "Yura", "login", "yura@example.org", "roles", List.of("VIEWER"), "active", true))))
            .andExpect(status().isOk()).andExpect(jsonPath("credentialsVersion").value(0));
        verifyNoInteractions(sessions);
        mvc.perform(put("/api/admin/users/"+id).with(user("a").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(body(Map.of("name", "Yura", "login", "yura@example.org", "roles", List.of("EDITOR"), "active", false))))
            .andExpect(status().isOk()).andExpect(jsonPath("credentialsVersion").value(1));
        assertFalse(service.matchesPassword(service.findById(id), PASSWORD));
        mvc.perform(post("/api/admin/users/"+id+"/reset-password").with(user("a").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(body(Map.of("temporaryPassword", "Another-12345!"))))
            .andExpect(status().isOk()).andExpect(jsonPath("credentialsVersion").value(2)).andExpect(jsonPath("mustChangePassword").value(true))
            .andExpect(jsonPath("temporaryPassword").doesNotExist());
        verify(sessions, times(2)).revokeAll(id);
    }
    @Test void changePasswordValidatesCurrentAndRequiresNewLogin() throws Exception {
        UUID id = create();
        assertThrows(AccountProblem.class, () -> service.changePassword(id, "wrong", "Next-Password-123!"));
        assertThrows(AccountProblem.class, () -> service.changePassword(id, PASSWORD, PASSWORD));
        assertThrows(AccountProblem.class, () -> service.changePassword(id, PASSWORD, "weak"));
        assertEquals(0, service.findById(id).getCredentialsVersion());
        service.changePassword(id, PASSWORD, "Next-Password-123!");
        UserAccount account = service.findById(id);
        assertFalse(account.isMustChangePassword());
        assertEquals(1, account.getCredentialsVersion());
        assertTrue(service.matchesPassword(account, "Next-Password-123!"));
        assertFalse(service.matchesPassword(account, PASSWORD));
        verify(sessions).revokeAll(id);
    }
    @Test void rejectsDirectHttpForViewerEditorAndAnonymous() throws Exception {
        UUID id = create();
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        for (String role : List.of("VIEWER", "EDITOR")) {
            mvc.perform(get("/api/admin/users").with(user("u").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/users").with(user("u").roles(role)).with(csrf()).contentType("application/json")
                .content(body(Map.of("name", "Other", "login", "other@example.org", "roles", List.of("VIEWER"), "temporaryPassword", PASSWORD))))
                .andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/users/"+id).with(user("u").roles(role)).with(csrf()).contentType("application/json")
                .content(body(Map.of("name", "Other", "login", "other@example.org", "roles", List.of("VIEWER"), "active", true))))
                .andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/users/"+id+"/reset-password").with(user("u").roles(role)).with(csrf()).contentType("application/json")
                .content(body(Map.of("temporaryPassword", PASSWORD)))).andExpect(status().isForbidden());
            mvc.perform(post("/api/categories").with(user("u").roles(role)).with(csrf()).contentType("application/json")
                .content(body(Map.of("code", "DESIGN", "name", "Дизайн", "active", true)))).andExpect(status().isForbidden());
            mvc.perform(put("/api/categories/"+UUID.randomUUID()).with(user("u").roles(role)).with(csrf()).contentType("application/json")
                .content(body(Map.of("code", "DESIGN", "name", "Дизайн", "active", true)))).andExpect(status().isForbidden());
        }
    }
    @Test void categoryDisablesWithoutDeletingExistingReference() throws Exception {
        String result = mvc.perform(post("/api/categories").with(user("a").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(body(Map.of("code", "design", "name", "Дизайн", "active", true))))
            .andExpect(status().isCreated()).andExpect(jsonPath("code").value("DESIGN")).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(result).get("id").asText());
        mvc.perform(put("/api/categories/"+id).with(user("a").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(body(Map.of("code", "DESIGN", "name", "Дизайн", "active", false)))).andExpect(status().isOk());
        mvc.perform(get("/api/categories?active=true").with(user("u").roles("VIEWER"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/categories").with(user("u").roles("VIEWER"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].active").value(false));
        assertThrows(AccountProblem.class, () -> categoryService.requireAssignable(id, null));
        assertThrows(AccountProblem.class, () -> categoryService.requireAssignable(id, UUID.randomUUID()));
        assertEquals(id, categoryService.requireAssignable(id, id).getId());
        assertEquals(1, categories.count());
    }
    @Test void validationDoesNotEchoSecretsAndWriteOnlyDtosAreSafe() throws Exception {
        String secret = "weak-secret";
        String response = mvc.perform(post("/api/admin/users").with(user("a").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(body(Map.of("name", "Yura", "login", "yura@example.org", "roles", List.of("VIEWER"), "temporaryPassword", secret))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("fieldErrors[0].field").value("temporaryPassword"))
            .andExpect(header().exists("X-Correlation-ID")).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(secret));
        assertFalse(body(new AccountDtos.Reset(PASSWORD)).contains(PASSWORD));
        assertFalse(new AccountDtos.Create("Yura", "yura@example.org", Set.of(AccessRole.ADMIN), PASSWORD).toString().contains(PASSWORD));
        verifyNoInteractions(audit, sessions);
    }
    @Test void bootstrapNeverResetsAnExistingAccount() {
        var first = service.createBootstrapAdmin("Admin", "admin@example.org", PASSWORD);
        var again = service.createBootstrapAdmin("Changed", "ADMIN@example.org", "Different-12345!");
        assertEquals(first.getId(), again.getId());
        assertTrue(service.matchesPassword(again, PASSWORD));
        assertEquals(Set.of(AccessRole.ADMIN), again.getRoles());
        assertEquals(1, accounts.count());
    }
}
