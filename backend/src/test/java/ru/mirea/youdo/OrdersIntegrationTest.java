package ru.mirea.youdo;

import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mirea.youdo.domain.*;
import ru.mirea.youdo.dto.OrderDtos.*;
import ru.mirea.youdo.error.ApiException;
import ru.mirea.youdo.filter.OrderFilter;
import ru.mirea.youdo.repository.OrderRepository;
import ru.mirea.youdo.security.*;
import ru.mirea.youdo.service.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=YoudoApplication.class,properties={
    "spring.datasource.hikari.connection-init-sql=SET search_path TO orders_tests",
    "spring.flyway.schemas=orders_tests","spring.flyway.default-schema=orders_tests",
    "spring.jpa.properties.hibernate.default_schema=orders_tests",
    "youdo.permanent-delete-enabled=true"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Import(OrdersIntegrationTest.TestTime.class)
class OrdersIntegrationTest {
    static final Instant NOW=Instant.parse("2026-11-21T21:30:00Z");
    static final LocalDate TODAY=LocalDate.of(2026,11,22);
    @TestConfiguration static class TestTime {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW,ZoneOffset.UTC); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @Autowired AccountCredentialsStore credentials;
    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired AuditWriter audit;
    @Autowired EntityManager entities;
    @Autowired PlatformTransactionManager manager;
    UUID category,actor;
    long customer,freelancer;
    String editor,viewer,admin,temporary;
    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE orders, users, user_accounts, audit_events, auth_login_attempts RESTART IDENTITY CASCADE");
        jdbc.update("update categories set active=true");
        category=jdbc.queryForObject("select id from categories where code='DEVELOPMENT'",UUID.class);
        customer=jdbc.queryForObject("insert into users(name,email,role) values ('Заказчик','customer@example.org','CUSTOMER') returning id",Long.class);
        freelancer=jdbc.queryForObject("insert into users(name,email,role) values ('Исполнитель','freelancer@example.org','FREELANCER') returning id",Long.class);
        editor=account("EDITOR",false);viewer=account("VIEWER",false);admin=account("ADMIN",false);temporary=account("EDITOR",true);
        actor=jdbc.queryForObject("select id from user_accounts where login='editorfalse@example.org'",UUID.class);
        authenticate();
    }
    @AfterEach void clearPrincipal() { SecurityContextHolder.clearContext(); }
    String account(String role,boolean temp) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into user_accounts(id,name,login,password_hash,must_change_password) values (?,?,?,?,?)",id,role,role.toLowerCase()+temp+"@example.org","unused-test-hash",temp);
        jdbc.update("insert into account_roles(account_id,role) values (?,?)",id,role);
        return tokens.access(credentials.findById(id).orElseThrow());
    }
    void authenticate() {
        var user=new CurrentUser(actor,"Editor","editorfalse@example.org",Set.of("EDITOR"),false,0);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,
            List.of(new SimpleGrantedAuthority("ROLE_EDITOR"),new SimpleGrantedAuthority("ROLE_VIEWER"))));
    }
    Map<String,Object> input(String code) {
        var i=new LinkedHashMap<String,Object>();
        i.put("code",code);i.put("title","Разработка сайта");i.put("description","Только описание: иголка 50% _");
        i.put("categoryId",category);i.put("status","OPEN");i.put("budget","123.4500");i.put("deadline",TODAY.plusDays(3).toString());
        i.put("customerId",customer);i.put("freelancerId",null);i.put("urgent",false);i.put("tags",List.of(" Java ","JAVA","веб"));
        return i;
    }
    Write write(Map<String,Object> input) { return json.convertValue(input,Write.class); }
    Response create(String code) { authenticate();return service.create(write(input(code))); }
    JsonNode send(MockHttpServletRequestBuilder request,String token,Object body,int expected) throws Exception {
        request.header("Authorization","Bearer "+token);
        if(body!=null) request.contentType("application/json").content(json.writeValueAsString(body));
        var result=mvc.perform(request).andExpect(status().is(expected)).andExpect(header().exists("X-Correlation-ID")).andReturn();
        String text=result.getResponse().getContentAsString();return text.isEmpty()?json.nullNode():json.readTree(text);
    }
    long count(String table) { return jdbc.queryForObject("select count(*) from "+table,Long.class); }

    @Test void realHttpCrudMoneyParticipantsVersionAndAudit() throws Exception {
        SecurityContextHolder.clearContext();
        var made=send(post("/api/orders"),editor,input("WEB-1"),201);UUID id=UUID.fromString(made.get("id").asText());
        assertThat(made.get("budget").isTextual()).isTrue();assertThat(made.get("budget").asText()).isEqualTo("123.45");
        assertThat(made.get("priority").asText()).isEqualTo("MEDIUM");assertThat(made.get("tags").size()).isEqualTo(2);
        var updated=input("WEB-1");updated.put("version",0);updated.put("status","IN_PROGRESS");updated.put("freelancerId",freelancer);
        send(put("/api/orders/"+id),editor,updated,200);
        send(put("/api/orders/"+id),editor,updated,409);
        assertThat(count("orders")).isEqualTo(1);assertThat(count("audit_events")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select version from orders",Long.class)).isEqualTo(1);
        var p=send(get("/api/participants?role=CUSTOMER&q=customer&size=1"),viewer,null,200);
        assertThat(p.get("totalElements").asInt()).isEqualTo(1);
        assertThat(p.at("/content/0/id").asLong()).isEqualTo(customer);
        send(get("/api/admin/audit?action=ORDER_UPDATED"),admin,null,200);
    }
    @Test void sharedPreviewAndWriteValidationNoBusinessOrAuditWrites() {
        var input=input("CSV-1");service.validateCreate(write(input));
        assertThat(count("orders")).isZero();assertThat(count("audit_events")).isZero();
        var o=create("CSV-1");input.put("version",o.version());
        service.validateUpdateByCode("CSV-1",write(input));
        assertThat(count("audit_events")).isEqualTo(1);
        var next=service.updateByCode("CSV-1",write(input));assertThat(next.version()).isGreaterThan(o.version());
        assertThatThrownBy(()->service.validateUpdateByCode("CSV-1",write(input))).isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("VERSION_CONFLICT");
    }
    @Test void invalidBudgetsDatesRolesAndTransitionsAreAtomic() throws Exception {
        for(Object bad:List.of("0","-1","10000000000","1.231","NaN")) {
            var i=input("BAD");i.put("budget",bad);send(post("/api/orders"),editor,i,400);
        }
        for(var bad:List.of(Map.of("deadline",TODAY.minusDays(1).toString()),Map.of("customerId",freelancer),
                Map.of("freelancerId",customer),Map.of("title"," "),Map.of("tags",List.of("bad|tag")),Map.of("status","IN_PROGRESS"))) {
            var i=input("BAD");i.putAll(bad);send(post("/api/orders"),editor,i,400);
        }
        assertThat(count("orders")).isZero();assertThat(count("audit_events")).isZero();
        var o=create("RULES");var update=input("RULES");update.put("version",o.version());update.put("status","COMPLETED");
        send(put("/api/orders/"+o.id()),editor,update,400);
        assertThat(count("audit_events")).isEqualTo(1);
        long other=jdbc.queryForObject("insert into users(name,email,role) values ('Другой','other@example.org','CUSTOMER') returning id",Long.class);
        update.put("status","OPEN");update.put("customerId",other);send(put("/api/orders/"+o.id()),editor,update,400);
    }
    @Test void disabledCategoryCanBeKeptButCannotBeAssigned() throws Exception {
        var o=create("OLD");jdbc.update("update categories set active=false where id=?",category);
        var same=input("OLD");same.put("version",o.version());send(put("/api/orders/"+o.id()),editor,same,200);
        send(post("/api/orders"),editor,input("NEW"),400);
    }
    @Test void filtersAndExportUseSqlAndSharePriorityDate() throws Exception {
        create("MATCH");
        var low=input("LOW");low.put("deadline",TODAY.plusDays(4).toString());service.create(write(low));
        var high=input("HIGH");high.put("urgent",true);service.create(write(high));
        var finalOrder=input("FINAL");finalOrder.put("urgent",true);finalOrder.put("status","CANCELLED");service.create(write(finalOrder));
        var q=get("/api/orders").param("q","ИГОЛКА").param("categoryId",category.toString()).param("status","OPEN")
            .param("tag"," JAVA ").param("urgent","false").param("priority","MEDIUM")
            .param("budgetOp","gte").param("budgetValue","123.45").param("sort","budget,asc").param("size","1");
        var result=send(q,viewer,null,200);assertThat(result.get("totalElements").asInt()).isEqualTo(1);
        assertThat(result.at("/content/0/code").asText()).isEqualTo("MATCH");
        assertThat(send(get("/api/orders").param("q","%"),viewer,null,200).get("totalElements").asInt()).isEqualTo(4);
        assertThat(send(get("/api/orders").param("q","_"),viewer,null,200).get("totalElements").asInt()).isEqualTo(4);
        var hostile=send(get("/api/orders").param("q","' OR 1=1 --"),viewer,null,200);
        assertThat(hostile.get("totalElements").asInt()).isZero();
        var range=send(get("/api/orders?budgetFrom=120&budgetTo=124&size=1&sort=title,asc"),viewer,null,200);
        assertThat(range.get("totalElements").asInt()).isEqualTo(4);assertThat(range.get("content").size()).isEqualTo(1);
        authenticate();var exported=service.findForExport(OrderFilter.from(Map.of()),"budget,desc");
        assertThat(exported).hasSize(4);
        assertThat(exported).allMatch(r->r.categoryCode().equals("DEVELOPMENT") && r.customerEmail().equals("customer@example.org"));
        for(var priority:OrderPriority.values()) {
            var page=service.list(OrderFilter.from(Map.of("priority",priority.name())),0,100,"createdAt,desc");
            assertThat(page.content()).allMatch(o->o.priority()==priority);
        }
        for(String invalid:List.of("size=101","page=-1","sort=description,asc","sort=title,up","budgetOp=sql&budgetValue=1",
                "urgent=maybe","archived=yes","budgetFrom=2&budgetTo=1","budgetOp=eq","priority=TOP"))
            send(get("/api/orders?"+invalid),viewer,null,400);
    }
    @Test void archiveRestoreFinalRulesAndPermanentRemovalKeepAudit() throws Exception {
        var o=create("ARCH");
        var i=input("ARCH");i.put("status","CANCELLED");i.put("version",0);
        var finalOrder=send(put("/api/orders/"+o.id()),editor,i,200);
        i.put("version",finalOrder.get("version").asLong());i.put("title","Менять нельзя");send(put("/api/orders/"+o.id()),editor,i,409);
        i.put("title","Разработка сайта");i.put("tags",List.of("только тег"));
        var tagged=send(put("/api/orders/"+o.id()),editor,i,200);long version=tagged.get("version").asLong();
        send(delete("/api/orders/"+o.id()+"?version="+version),editor,null,204);
        send(delete("/api/orders/"+o.id()+"?version="+version),editor,null,409);
        send(put("/api/orders/"+o.id()),editor,i,409);
        var archived=send(get("/api/orders/"+o.id()),viewer,null,200);long av=archived.get("version").asLong();
        send(delete("/api/orders/"+o.id()+"?version="+av),editor,null,204);
        var restored=send(post("/api/orders/"+o.id()+"/restore"),editor,Map.of("version",av),200);
        assertThat(restored.get("status").asText()).isEqualTo("CANCELLED");
        send(delete("/api/orders/"+o.id()+"/permanent?version="+restored.get("version").asLong()),admin,null,409);
        send(delete("/api/orders/"+o.id()+"?version="+restored.get("version").asLong()),editor,null,204);
        var current=send(get("/api/orders/"+o.id()),viewer,null,200);
        send(delete("/api/orders/"+o.id()+"/permanent?version="+current.get("version").asLong()),editor,null,403);
        send(delete("/api/orders/"+o.id()+"/permanent?version="+current.get("version").asLong()),admin,null,204);
        assertThat(count("orders")).isZero();assertThat(count("order_tags")).isZero();assertThat(count("audit_events")).isGreaterThan(4);
    }
    @Test void bulkPartialSuccessAndNotesBelongToTheirOrder() throws Exception {
        var a=create("A");var b=create("B");
        var note=send(post("/api/orders/"+a.id()+"/notes"),editor,Map.of("text","Личный текст не для аудита","pinned",true),201);
        assertThat(note.get("authorId").asText()).isEqualTo(actor.toString());
        send(put("/api/orders/"+b.id()+"/notes/"+note.get("id").asText()),editor,Map.of("text","x","pinned",false),404);
        send(delete("/api/orders/"+b.id()+"/notes/"+note.get("id").asText()),editor,null,404);
        send(put("/api/orders/"+a.id()+"/notes/"+note.get("id").asText()),editor,Map.of("text","Новый текст","pinned",false),200);
        send(get("/api/orders/"+a.id()+"/notes"),viewer,null,200);
        var result=send(post("/api/orders/bulk"),editor,Map.of("action","ARCHIVE","items",List.of(
            Map.of("id",a.id(),"version",0),Map.of("id",b.id(),"version",0),Map.of("id",UUID.randomUUID(),"version",0))),200);
        assertThat(result.get("succeeded").size()).isEqualTo(1);assertThat(result.get("failed").size()).isEqualTo(2);
        assertThat(result.at("/failed/0/code").asText()).isEqualTo("VERSION_CONFLICT");
        var current=send(get("/api/orders/"+a.id()),viewer,null,200);
        send(delete("/api/orders/"+a.id()+"?version="+current.get("version").asLong()),editor,null,204);
        send(post("/api/orders/"+a.id()+"/notes"),editor,Map.of("text","x","pinned",false),409);
        assertThat(jdbc.queryForList("select summary from audit_events",String.class)).noneMatch(s->s.contains("Личный текст"));
        var archived=send(get("/api/orders/"+a.id()),viewer,null,200);
        send(delete("/api/orders/"+a.id()+"/permanent?version="+archived.get("version").asLong()),admin,null,204);
        assertThat(count("order_notes")).isZero();
    }
    @Test void rolesEnforcedForHttpAndInternalService() throws Exception {
        SecurityContextHolder.clearContext();mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        send(post("/api/orders"),viewer,input("NO"),403);send(post("/api/orders"),temporary,input("NO"),403);
        send(get("/api/orders"),viewer,null,200);
        var u=new CurrentUser(actor,"V","v@example.org",Set.of("VIEWER"),false,0);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,List.of(new SimpleGrantedAuthority("ROLE_VIEWER"))));
        assertThatThrownBy(()->service.create(write(input("NO")))).isInstanceOf(ApiException.class);
    }
    @Test void enclosingCsvTransactionRollsBackOrdersAndAuditTogether() {
        var tx=new TransactionTemplate(manager);
        assertThatThrownBy(()->tx.execute(s->{service.create(write(input("ROLLBACK")));throw new IllegalStateException("simulate CSV failure");}))
            .isInstanceOf(IllegalStateException.class);
        assertThat(count("orders")).isZero();assertThat(count("audit_events")).isZero();
    }
    @Test void postgresOptimisticRaceAllowsOneCommitIncludingAudit() throws Exception {
        var o=create("RACE");var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> task=()-> {
                authenticate();
                try {
                    new TransactionTemplate(manager).execute(s->{
                        var entity=orders.findByPublicId(o.id()).orElseThrow();
                        try { barrier.await(10,TimeUnit.SECONDS); } catch(Exception e) { throw new RuntimeException(e); }
                        entity.touch(NOW.plusSeconds(1));
                        audit.record("ORDER_RACE","ORDER",o.id(),"Проверка конкуренции");
                        orders.flush();return null;
                    });
                    return true;
                } catch(org.springframework.dao.OptimisticLockingFailureException e) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            };
            var one=pool.submit(task);var two=pool.submit(task);
            assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(count("audit_events")).isEqualTo(2);assertThat(jdbc.queryForObject("select version from orders",Long.class)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
}
