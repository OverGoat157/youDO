package ru.mirea.youdo;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import ru.mirea.youdo.config.*;
import ru.mirea.youdo.dto.AuthDtos.*;
import ru.mirea.youdo.error.ApiException;
import ru.mirea.youdo.security.*;
import ru.mirea.youdo.service.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@Import(AuthIntegrationTest.SecurityProbe.class)
class AuthIntegrationTest {
    private static final String PASSWORD="Valid-Password123!";
    private static final String NEXT="Different-Password456!";
    private static final String ORIGIN="http://localhost:8080";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountCredentialsStore accounts;
    @Autowired AuthService auth;
    @Autowired TokenService tokens;
    @Autowired SessionRevoker revoker;
    @Autowired AuditWriter audit;
    @Autowired PlatformTransactionManager transactions;
    @Autowired YoudoProperties properties;
    @Autowired NewPasswordPolicy passwordPolicy;

    @RestController
    static class SecurityProbe {
        @GetMapping("/api/orders/_probe") Map<String,String> read() { return Map.of("result","read"); }
        @PostMapping("/api/orders/_probe") Map<String,String> write() { return Map.of("result","write"); }
        @DeleteMapping("/api/orders/_probe/permanent") Map<String,String> permanent() { return Map.of("result","delete"); }
        @GetMapping("/api/admin/_probe") Map<String,String> admin() { return Map.of("result","admin"); }
        @PutMapping("/api/categories/_probe") Map<String,String> category() { return Map.of("result","category"); }
    }
    @BeforeEach void reset() {
        // Dedicated auth_tests schema in the test database; never the application's public schema.
        jdbc.execute("TRUNCATE refresh_sessions,account_roles,order_notes,user_accounts,audit_events,auth_login_attempts");
    }
    UUID account(String role, boolean temporary) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into user_accounts(id,name,login,password_hash,must_change_password) values (?,?,?,?,?)",
            id,"Проверка",role.toLowerCase()+"@example.org",encoder.encode(PASSWORD),temporary);
        jdbc.update("insert into account_roles(account_id,role) values (?,?)",id,role);
        return id;
    }
    JsonNode login(String role) throws Exception {
        return loginBody(role.toLowerCase()+"@example.org",PASSWORD,"ANDROID",null,null,200);
    }
    JsonNode loginBody(String login, String password, String type, String origin, Cookie cookie, int status) throws Exception {
        var request=post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("login",login,"password",password,"clientType",type)));
        if (origin != null) request.header("Origin",origin);
        if (cookie != null) request.cookie(cookie);
        var result=mvc.perform(request).andExpect(status().is(status)).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    ResultActions session(String endpoint, String token) throws Exception {
        return mvc.perform(post("/api/auth/"+endpoint).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("clientType","ANDROID","refreshToken",token))));
    }
    @Test void contextAndFlywayUsePostgres() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success and version is not null",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from categories",Integer.class)).isEqualTo(5);
    }
    @Test void loginMeHashAndCorrelation() throws Exception {
        UUID id=account("VIEWER",false);
        var response=mvc.perform(post("/api/auth/login").header("X-Correlation-ID","login_check-1")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new LoginRequest("VIEWER@example.org",PASSWORD,ClientType.ANDROID))))
            .andExpect(status().isOk()).andExpect(header().string("X-Correlation-ID","login_check-1"))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.expiresIn").value(900)).andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn();
        var body=json.readTree(response.getResponse().getContentAsString());
        String token=body.path("accessToken").asText();
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.passwordHash").doesNotExist());
        String stored=jdbc.queryForObject("select token_hash from refresh_sessions",String.class);
        assertThat(stored).isEqualTo(TokenService.hash(body.path("refreshToken").asText()));
        assertThat(response.getResponse().getContentAsString()).doesNotContain(PASSWORD,"$2a$");
        assertThat(jdbc.queryForObject("select correlation_id from audit_events where action='LOGIN'",String.class)).isEqualTo("login_check-1");
    }
    @Test void roleHierarchyAndServerEnforcement() throws Exception {
        for (String role: List.of("VIEWER","EDITOR","ADMIN")) {
            account(role,false);
            String token=login(role).path("accessToken").asText();
            mvc.perform(get("/api/orders/_probe").header("Authorization","Bearer "+token)).andExpect(status().isOk());
            mvc.perform(post("/api/orders/_probe").header("Authorization","Bearer "+token)).andExpect(status().is(role.equals("VIEWER") ? 403 : 200));
            mvc.perform(get("/api/admin/_probe").header("Authorization","Bearer "+token)).andExpect(status().is(role.equals("ADMIN") ? 200 : 403));
            mvc.perform(put("/api/categories/_probe").header("Authorization","Bearer "+token)).andExpect(status().is(role.equals("ADMIN") ? 200 : 403));
            mvc.perform(delete("/api/orders/_probe/permanent").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
            mvc.perform(get("/v3/api-docs").header("Authorization","Bearer "+token)).andExpect(status().is(role.equals("ADMIN") ? 200 : 403));
        }
    }
    @Test void temporaryPasswordBlocksApisAndRefreshUntilChange() throws Exception {
        account("ADMIN",true);
        var logged=login("ADMIN");
        String token=logged.path("accessToken").asText();
        mvc.perform(get("/api/orders/_probe").header("Authorization","Bearer "+token))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        session("refresh",logged.path("refreshToken").asText()).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/change-password").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new ChangePasswordRequest(PASSWORD,NEXT))))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        session("refresh",logged.path("refreshToken").asText()).andExpect(status().isUnauthorized());
        loginBody("admin@example.org",PASSWORD,"ANDROID",null,null,401);
        var renewed=loginBody("admin@example.org",NEXT,"ANDROID",null,null,200);
        assertThat(renewed.path("user").path("mustChangePassword").asBoolean()).isFalse();
    }
    @Test void refreshRotatesLogoutRevokesButAccessSurvivesLogout() throws Exception {
        account("VIEWER",false);
        var logged=login("VIEWER");
        String old=logged.path("refreshToken").asText();
        var result=session("refresh",old).andExpect(status().isOk()).andReturn();
        String next=json.readTree(result.getResponse().getContentAsString()).path("refreshToken").asText();
        assertThat(next).isNotEqualTo(old);
        session("refresh",old).andExpect(status().isUnauthorized());
        session("logout",next).andExpect(status().isNoContent());
        session("refresh",next).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+logged.path("accessToken").asText())).andExpect(status().isOk());
    }
    @Test void concurrentRefreshAllowsOnlyOneWinner() throws Exception {
        account("VIEWER",false);
        String old=login("VIEWER").path("refreshToken").asText();
        try (var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Integer> task=() -> {
                try { auth.refresh(old,ClientType.ANDROID); return 200; }
                catch (ApiException e) { return e.status(); }
            };
            var results=executor.invokeAll(List.of(task,task));
            assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,401);
        }
    }
    @Test void expiredRefreshAndExpiredAccessAreRejected() throws Exception {
        UUID id=account("VIEWER",false);
        var logged=login("VIEWER");
        jdbc.update("update refresh_sessions set expires_at=now()-interval '1 second'");
        session("refresh",logged.path("refreshToken").asText()).andExpect(status().isUnauthorized());
        var future=Clock.fixed(Instant.now().minus(Duration.ofHours(1)),ZoneOffset.UTC);
        String expired=new TokenService(properties,future).access(accounts.findById(id).orElseThrow());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
    }
    @Test void roleChangeCredentialsVersionAndDisableInvalidateTokens() throws Exception {
        UUID id=account("ADMIN",false);
        var logged=login("ADMIN");
        new TransactionTemplate(transactions).executeWithoutResult(s -> {
            accounts.lock(id);
            jdbc.update("update account_roles set role='VIEWER' where account_id=?",id);
            jdbc.update("update user_accounts set credentials_version=credentials_version+1 where id=?",id);
            revoker.revokeAll(id);
        });
        mvc.perform(get("/api/admin/_probe").header("Authorization","Bearer "+logged.path("accessToken").asText())).andExpect(status().isUnauthorized());
        session("refresh",logged.path("refreshToken").asText()).andExpect(status().isUnauthorized());
        var renewed=login("ADMIN"); // login name is unchanged; effective roles now VIEWER.
        mvc.perform(get("/api/admin/_probe").header("Authorization","Bearer "+renewed.path("accessToken").asText())).andExpect(status().isForbidden());
        jdbc.update("update user_accounts set active=false where id=?",id);
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+renewed.path("accessToken").asText())).andExpect(status().isUnauthorized());
        session("refresh",renewed.path("refreshToken").asText()).andExpect(status().isUnauthorized());
        loginBody("admin@example.org",PASSWORD,"ANDROID",null,null,401);
    }
    @Test void fiveFailedAttemptsLockEvenUnknownLoginAndRecoverAfterWindow() throws Exception {
        for (int n=0;n<5;n++) loginBody("unknown@example.org",PASSWORD,"ANDROID",null,null,401);
        loginBody("unknown@example.org",PASSWORD,"ANDROID",null,null,429);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action='LOGIN_FAILED'",Integer.class)).isEqualTo(5);
        jdbc.update("update auth_login_attempts set blocked_until=now()-interval '1 second', updated_at=now()-interval '16 minutes'");
        loginBody("unknown@example.org",PASSWORD,"ANDROID",null,null,401);
    }
    @Test void successfulLoginResetsFailuresAndClientCannotSpoofIp() throws Exception {
        account("VIEWER",false);
        for (int n=0;n<4;n++) loginBody("viewer@example.org","Wrong-password1!","ANDROID",null,null,401);
        login("VIEWER");
        assertThat(jdbc.queryForObject("select count(*) from auth_login_attempts",Integer.class)).isZero();
        for (int n=0;n<5;n++) {
            mvc.perform(post("/api/auth/login").header("X-Forwarded-For","10.0.0."+n).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest("viewer@example.org","Wrong-password1!",ClientType.ANDROID))))
                .andExpect(status().isUnauthorized());
        }
        loginBody("viewer@example.org",PASSWORD,"ANDROID",null,null,429);
    }
    @Test void webUsesCookieAndStrictOriginProtectsLoginRefreshLogout() throws Exception {
        account("VIEWER",false);
        loginBody("viewer@example.org",PASSWORD,"WEB",null,null,403);
        loginBody("viewer@example.org",PASSWORD,"ANDROID","https://hostile.example",null,403);
        var result=mvc.perform(post("/api/auth/login").header("Origin",ORIGIN).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new LoginRequest("viewer@example.org",PASSWORD,ClientType.WEB))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn();
        String setCookie=result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("HttpOnly","SameSite=Strict","Path=/api/auth");
        String value=setCookie.substring(setCookie.indexOf('=')+1,setCookie.indexOf(';'));
        var cookie=new Cookie(AuthTransport.COOKIE,value);
        mvc.perform(post("/api/auth/refresh").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"clientType\":\"WEB\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"clientType\":\"ANDROID\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh").cookie(cookie).header("Origin",ORIGIN).contentType(MediaType.APPLICATION_JSON).content("{\"clientType\":\"WEB\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").doesNotExist());
        mvc.perform(post("/api/auth/logout").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"clientType\":\"WEB\"}"))
            .andExpect(status().isForbidden());
    }
    @Test void clientTypeCannotSwitchRefreshTransport() throws Exception {
        account("VIEWER",false);
        String raw=login("VIEWER").path("refreshToken").asText();
        mvc.perform(post("/api/auth/refresh").header("Origin",ORIGIN).cookie(new Cookie(AuthTransport.COOKIE,raw))
            .contentType(MediaType.APPLICATION_JSON).content("{\"clientType\":\"WEB\"}"))
            .andExpect(status().isUnauthorized());
    }
    @Test void errorsShareJsonAndSanitizeCorrelation() throws Exception {
        mvc.perform(get("/api/orders/_probe").header("X-Correlation-ID","invalid id"))
            .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.fieldErrors").isArray())
            .andExpect(jsonPath("$.correlationId").isNotEmpty()).andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(jsonPath("$.trace").doesNotExist()).andExpect(header().string("X-Correlation-ID",org.hamcrest.Matchers.not("invalid id")));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{broken"))
            .andExpect(status().isBadRequest());
        account("VIEWER",false);
        String token=login("VIEWER").path("accessToken").asText();
        mvc.perform(get("/api/missing").header("Authorization","Bearer "+token)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
    @Test void passwordPolicyEnforcesUtf8BcryptLimitAndCategories() {
        for (String invalid: List.of("shortA1!","nouppercase123!","NOLOWERCASE123!","MissingDigits!!!","MissingSpecial123", "Я".repeat(35)+"я123!"))
            assertThatThrownBy(() -> passwordPolicy.validate(invalid)).isInstanceOf(ApiException.class);
        assertThatCode(() -> passwordPolicy.validate(PASSWORD)).doesNotThrowAnyException();
    }
    @Test void successfulAuditRollsBackWithBusinessChange() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            audit.record("ORDER_UPDATED","ORDER",UUID.randomUUID(),"Заказ изменён");
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from audit_events",Integer.class)).isZero();
    }
    @Test void bootstrapIsHashedTemporaryAndNeverResetsExistingPassword() throws Exception {
        var b=new YoudoProperties(properties.jwtSecret(),properties.issuer(),properties.accessDuration(),properties.refreshDuration(),false,
            properties.allowedOrigins(),false,new YoudoProperties.Bootstrap("Bootstrap@Example.org","Администратор",PASSWORD));
        var runner=new BootstrapAdmin(b,accounts,encoder,passwordPolicy);
        runner.run(null);
        var before=accounts.findByLogin("bootstrap@example.org").orElseThrow();
        assertThat(before.mustChangePassword()).isTrue();
        assertThat(before.roles()).containsExactly("ADMIN");
        assertThat(encoder.matches(PASSWORD,before.passwordHash())).isTrue();
        runner.run(null);
        assertThat(accounts.findByLogin(before.login()).orElseThrow().passwordHash()).isEqualTo(before.passwordHash());
    }
}
