package ru.mirea.youdo;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.mirea.youdo.security.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Сводка по заказам на настоящем PostgreSQL: счётчики, средний бюджет и три группировки. */
@SpringBootTest(classes=YoudoApplication.class,properties={
    "spring.datasource.hikari.connection-init-sql=SET search_path TO analytics_tests",
    "spring.flyway.schemas=analytics_tests","spring.flyway.default-schema=analytics_tests",
    "spring.jpa.properties.hibernate.default_schema=analytics_tests"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Import(AnalyticsIntegrationTest.TestTime.class)
class AnalyticsIntegrationTest {
    static final Instant NOW=Instant.parse("2026-11-21T21:30:00Z"); // в Москве уже 22.11
    static final LocalDate TODAY=LocalDate.of(2026,11,22);
    @TestConfiguration static class TestTime {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW,ZoneOffset.UTC); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @Autowired AccountCredentialsStore credentials;
    UUID development,other;
    String otherCode,editor,viewer;
    long customer,freelancer;

    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE orders, users, user_accounts, audit_events, auth_login_attempts RESTART IDENTITY CASCADE");
        jdbc.update("update categories set active=true");
        development=jdbc.queryForObject("select id from categories where code='DEVELOPMENT'",UUID.class);
        other=jdbc.queryForObject("select id from categories where code<>'DEVELOPMENT' order by code limit 1",UUID.class);
        otherCode=jdbc.queryForObject("select code from categories where id=?",String.class,other);
        customer=jdbc.queryForObject("insert into users(name,email,role) values ('Заказчик','customer@example.org','CUSTOMER') returning id",Long.class);
        freelancer=jdbc.queryForObject("insert into users(name,email,role) values ('Исполнитель','freelancer@example.org','FREELANCER') returning id",Long.class);
        editor=account("EDITOR");viewer=account("VIEWER");
    }
    String account(String role) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into user_accounts(id,name,login,password_hash,must_change_password) values (?,?,?,?,false)",id,role,role.toLowerCase()+"@example.org","unused-test-hash");
        jdbc.update("insert into account_roles(account_id,role) values (?,?)",id,role);
        return tokens.access(credentials.findById(id).orElseThrow());
    }
    JsonNode send(MockHttpServletRequestBuilder request,String token,Object body,int expected) throws Exception {
        if(token!=null) request.header("Authorization","Bearer "+token);
        if(body!=null) request.contentType("application/json").content(json.writeValueAsString(body));
        String text=mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return text.isEmpty()?json.nullNode():json.readTree(text);
    }
    JsonNode order(String code,UUID category,String status,String budget,int daysLeft,boolean urgent) throws Exception {
        var i=new LinkedHashMap<String,Object>();
        i.put("code",code);i.put("title","Заказ "+code);i.put("description","");i.put("categoryId",category);i.put("status",status);
        i.put("budget",budget);i.put("deadline",TODAY.plusDays(daysLeft).toString());i.put("customerId",customer);
        i.put("freelancerId",status.equals("OPEN")?null:freelancer);i.put("urgent",urgent);i.put("tags",List.of());
        return send(post("/api/orders"),editor,i,201);
    }
    static long count(JsonNode groups,String key) {
        for(JsonNode g:groups) if(g.get("key").asText().equals(key)) return g.get("count").asLong();
        throw new AssertionError("Нет группы "+key);
    }

    @Test void summaryCountsActiveOrdersAndGroupsThemInTheDatabase() throws Exception {
        order("A",development,"OPEN","100.00",10,true);        // HIGH: срочный
        order("B",development,"IN_PROGRESS","200.00",2,false); // MEDIUM: дедлайн через 2 дня
        order("C",other,"OPEN","300.01",10,false);             // LOW
        order("D",other,"COMPLETED","400.00",1,true);          // LOW: завершён, срочность не важна
        var archived=order("E",development,"OPEN","5000.00",1,true);
        send(delete("/api/orders/"+archived.get("id").asText()+"?version=0"),editor,null,204);

        var s=send(get("/api/analytics/summary"),viewer,null,200);
        assertThat(s.get("total").asLong()).isEqualTo(5);
        assertThat(s.get("active").asLong()).isEqualTo(4);
        assertThat(s.get("archived").asLong()).isEqualTo(1);
        assertThat(s.get("urgent").asLong()).isEqualTo(2);
        // (100.00 + 200.00 + 300.01 + 400.00) / 4 = 250.0025 → 250.00; архивный 5000 не учитывается
        assertThat(s.get("averageBudget").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(s.get("priorityDate").asText()).isEqualTo(TODAY.toString());

        assertThat(s.get("byStatus").findValuesAsText("key")).containsExactly("OPEN","IN_PROGRESS","ON_REVIEW","COMPLETED","CANCELLED");
        assertThat(count(s.get("byStatus"),"OPEN")).isEqualTo(2);
        assertThat(count(s.get("byStatus"),"ON_REVIEW")).isZero();
        assertThat(count(s.get("byPriority"),"HIGH")).isEqualTo(1);
        assertThat(count(s.get("byPriority"),"MEDIUM")).isEqualTo(1);
        assertThat(count(s.get("byPriority"),"LOW")).isEqualTo(2);
        assertThat(count(s.get("byCategory"),"DEVELOPMENT")).isEqualTo(2);
        assertThat(count(s.get("byCategory"),otherCode)).isEqualTo(2);

        // Сводка и фильтр списка считают приоритет одним выражением — числа совпадают.
        var low=send(get("/api/orders?priority=LOW"),viewer,null,200);
        assertThat(low.get("totalElements").asLong()).isEqualTo(count(s.get("byPriority"),"LOW"));
    }

    @Test void emptyDatabaseGivesZerosAndNoAverage() throws Exception {
        var s=send(get("/api/analytics/summary"),viewer,null,200);
        assertThat(s.get("total").asLong()).isZero();
        assertThat(s.get("averageBudget").isNull()).isTrue();
        assertThat(s.get("byCategory").size()).isZero();
        assertThat(s.get("byStatus").size()).isEqualTo(5);
        assertThat(s.get("byPriority").findValuesAsText("key")).containsExactly("LOW","MEDIUM","HIGH");
    }

    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/analytics/summary")).andExpect(status().isUnauthorized());
    }
}
