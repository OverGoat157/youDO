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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.mirea.youdo.security.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CSV заказов на настоящем PostgreSQL: preview без записи, apply одной транзакцией, экспорт по фильтру. */
@SpringBootTest(classes=YoudoApplication.class,properties={
    "spring.datasource.hikari.connection-init-sql=SET search_path TO csv_tests",
    "spring.flyway.schemas=csv_tests","spring.flyway.default-schema=csv_tests",
    "spring.jpa.properties.hibernate.default_schema=csv_tests"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Import(CsvIntegrationTest.TestTime.class)
class CsvIntegrationTest {
    static final Instant NOW=Instant.parse("2026-11-21T21:30:00Z");
    static final LocalDate TODAY=LocalDate.of(2026,11,22);
    static final String HEADER="code;title;description;category;status;budget;deadline;customerEmail;freelancerEmail;urgent;tags\n";
    @TestConfiguration static class TestTime {
        @Bean @Primary Clock fixedClock() { return Clock.fixed(NOW,ZoneOffset.UTC); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @Autowired AccountCredentialsStore credentials;
    UUID category;
    String editor,otherEditor,viewer;

    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE orders, users, user_accounts, audit_events, auth_login_attempts RESTART IDENTITY CASCADE");
        jdbc.update("update categories set active=true");
        category=jdbc.queryForObject("select id from categories where code='DEVELOPMENT'",UUID.class);
        jdbc.update("insert into users(name,email,role) values ('Заказчик','customer@example.org','CUSTOMER')");
        jdbc.update("insert into users(name,email,role) values ('Исполнитель','freelancer@example.org','FREELANCER')");
        editor=account("EDITOR","editor");otherEditor=account("EDITOR","other");viewer=account("VIEWER","viewer");
    }
    String account(String role,String name) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into user_accounts(id,name,login,password_hash,must_change_password) values (?,?,?,?,false)",id,name,name+"@example.org","unused-test-hash");
        jdbc.update("insert into account_roles(account_id,role) values (?,?)",id,role);
        return tokens.access(credentials.findById(id).orElseThrow());
    }
    static String row(String code,String title,String budget) {
        return code+";"+title+";Описание;DEVELOPMENT;OPEN;"+budget+";"+TODAY.plusDays(10)+";customer@example.org;;false;java|веб\n";
    }
    JsonNode send(MockHttpServletRequestBuilder request,String token,Object body,int expected) throws Exception {
        if(token!=null) request.header("Authorization","Bearer "+token);
        if(body!=null) request.contentType("application/json").content(json.writeValueAsString(body));
        String text=mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return text.isEmpty()?json.nullNode():json.readTree(text);
    }
    JsonNode preview(String token,String csv,String mode,int expected) throws Exception {
        var file=new MockMultipartFile("file","orders.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8));
        return send(multipart("/api/orders/import/preview").file(file).param("mode",mode),token,null,expected);
    }
    JsonNode apply(String token,JsonNode preview,int expected) throws Exception {
        return send(post("/api/orders/import/apply"),token,Map.of("previewId",preview.get("previewId").asText()),expected);
    }
    JsonNode createOrder(String code,String title,boolean urgent) throws Exception {
        var i=new LinkedHashMap<String,Object>();
        i.put("code",code);i.put("title",title);i.put("description","Описание");i.put("categoryId",category);i.put("status","OPEN");
        i.put("budget","500.00");i.put("deadline",TODAY.plusDays(10).toString());i.put("customerId",1);i.put("freelancerId",null);
        i.put("urgent",urgent);i.put("tags",List.of("java"));
        return send(post("/api/orders"),editor,i,201);
    }
    long count(String sql) { return jdbc.queryForObject(sql,Long.class); }

    @Test void previewChecksRowsWithoutWritingAndApplyCreatesOnlyValidRows() throws Exception {
        String csv=HEADER+row("CSV-1","Сайт","1000.50")
            +"CSV-2;Лендинг;;NOPE;OPEN;10;"+TODAY.plusDays(5)+";customer@example.org;;false;\n"
            +row("CSV-1","Повтор","1.00")
            // Так файл выглядит после пересохранения в Excel: дата ДД.ММ.ГГГГ, бюджет с пробелом и запятой.
            +"CSV-3;Бот;;development;open;1 500,00;31.12.2026;CUSTOMER@example.org;;да;\n";
        var p=preview(editor,csv,"CREATE_ONLY",200);
        assertThat(p.get("totalRows").asInt()).isEqualTo(4);
        assertThat(p.get("validRows").asInt()).isEqualTo(2);
        assertThat(p.get("invalidRows").asInt()).isEqualTo(2);
        assertThat(p.get("toCreate").asInt()).isEqualTo(2);
        assertThat(p.at("/issues/0/line").asInt()).isEqualTo(3);
        assertThat(p.at("/issues/0/field").asText()).isEqualTo("category");
        assertThat(p.at("/issues/1/line").asInt()).isEqualTo(4);
        assertThat(p.at("/issues/1/field").asText()).isEqualTo("code");
        assertThat(count("select count(*) from orders")).isZero();
        assertThat(count("select count(*) from audit_events")).isZero();

        var result=apply(editor,p,200);
        assertThat(result.get("created").asInt()).isEqualTo(2);
        assertThat(result.get("skipped").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForMap("select budget::text b, urgent, deadline::text d from orders where code='CSV-3'"))
            .containsEntry("b","1500.00").containsEntry("urgent",true).containsEntry("d","2026-12-31");
        assertThat(count("select count(*) from audit_events where action='ORDER_CREATED'")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select summary from audit_events where action='ORDER_CSV_IMPORTED' and actor_login='editor@example.org'",String.class))
            .contains("создано 2").contains("пропущено 2");

        var again=apply(editor,p,404);
        assertThat(again.get("code").asText()).isEqualTo("PREVIEW_NOT_FOUND");
    }

    @Test void upsertUpdatesByCodeWhileCreateOnlySkipsExistingCode() throws Exception {
        createOrder("WEB-1","Старое название",false);
        String csv=HEADER+row("WEB-1","Новое название","2000.00")+row("WEB-2","Новый заказ","300.00");

        var createOnly=preview(editor,csv,"CREATE_ONLY",200);
        assertThat(createOnly.get("validRows").asInt()).isEqualTo(1);
        assertThat(createOnly.at("/issues/0/message").asText()).contains("уже есть");

        var upsert=preview(editor,csv,"UPSERT_BY_CODE",200);
        assertThat(upsert.get("toUpdate").asInt()).isEqualTo(1);
        assertThat(upsert.get("toCreate").asInt()).isEqualTo(1);
        var result=apply(editor,upsert,200);
        assertThat(result.get("updated").asInt()).isEqualTo(1);
        assertThat(result.get("created").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForMap("select title, version from orders where code='WEB-1'"))
            .containsEntry("title","Новое название").containsEntry("version",1L);
    }

    @Test void orderChangedAfterPreviewRollsBackWholeFileTogetherWithAudit() throws Exception {
        var made=createOrder("WEB-1","Название",false);
        // Первая строка — новый заказ: после отката его не должно остаться.
        var p=preview(editor,HEADER+row("WEB-2","Новый","100.00")+row("WEB-1","Из файла","100.00"),"UPSERT_BY_CODE",200);
        var change=json.convertValue(made,Map.class);
        for(String key:List.of("id","categoryName","customerName","freelancerName","priority","archived","createdAt","updatedAt")) change.remove(key);
        change.put("title","Изменено в форме");
        send(put("/api/orders/"+made.get("id").asText()),editor,change,200);
        long auditBefore=count("select count(*) from audit_events");

        var error=apply(editor,p,409);
        assertThat(error.get("code").asText()).isEqualTo("VERSION_CONFLICT");
        assertThat(error.get("message").asText()).startsWith("Строка 3");
        assertThat(count("select count(*) from orders")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select title from orders",String.class)).isEqualTo("Изменено в форме");
        assertThat(count("select count(*) from audit_events")).isEqualTo(auditBefore);
    }

    @Test void exportUsesListFilterEscapesFormulasAndReimportsUnchanged() throws Exception {
        createOrder("EXP-1","=HYPERLINK(1)",false);
        createOrder("EXP-2","Срочный",true);

        var response=mvc.perform(get("/api/orders/export?urgent=false&sort=title,asc").header("Authorization","Bearer "+viewer))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition","attachment; filename=\"orders-"+TODAY+".csv\""))
            .andReturn().getResponse();
        assertThat(response.getContentType()).startsWith("text/csv");
        String filtered=response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(filtered).startsWith("\uFEFFcode;title;description;category;status");
        assertThat(filtered).contains("EXP-1;'=HYPERLINK(1);").doesNotContain("EXP-2");

        String all=mvc.perform(get("/api/orders/export").header("Authorization","Bearer "+viewer))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var p=preview(editor,all,"UPSERT_BY_CODE",200);
        assertThat(p.get("validRows").asInt()).isEqualTo(2);
        assertThat(p.get("toUpdate").asInt()).isEqualTo(2);
        apply(editor,p,200);
        assertThat(jdbc.queryForObject("select title from orders where code='EXP-1'",String.class)).isEqualTo("=HYPERLINK(1)");
    }

    @Test void rolesOwnershipAndBrokenFiles() throws Exception {
        mvc.perform(get("/api/orders/export")).andExpect(status().isUnauthorized());
        preview(viewer,HEADER+row("X-1","Заказ","10.00"),"CREATE_ONLY",403);

        var p=preview(editor,HEADER+row("X-1","Заказ","10.00"),"CREATE_ONLY",200);
        assertThat(apply(otherEditor,p,404).get("code").asText()).isEqualTo("PREVIEW_NOT_FOUND");

        var noColumns=preview(editor,"code;title\nX-2;Заказ\n","CREATE_ONLY",400);
        assertThat(noColumns.get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(noColumns.get("message").asText()).contains("description");
        preview(editor,"","CREATE_ONLY",400);
        preview(editor,HEADER,"CREATE_ONLY",400);
        preview(editor,HEADER+row("X-3","Заказ","10.00"),"NO_SUCH_MODE",400);

        String comma=HEADER.replace(';',',')+row("X-4","Заказ","10.00").replace(';',',');
        assertThat(preview(editor,comma,"CREATE_ONLY",200).get("validRows").asInt()).isEqualTo(1);
        send(post("/api/orders/import/apply"),viewer,Map.of("previewId",UUID.randomUUID()),403);
        assertThat(count("select count(*) from orders")).isZero();
    }

    @Test void windows1251FromRussianExcelAndBlankRowsAreHandled() throws Exception {
        // Строки ;;;; Excel оставляет после очистки ячеек: их не считаем ни корректными, ни ошибочными.
        String csv=HEADER+row("W-1","Кириллица из Excel","10.00")+";;;;;;;;;;\n";
        var file=new MockMultipartFile("file","orders.csv","text/csv",csv.getBytes(java.nio.charset.Charset.forName("windows-1251")));
        var p=send(multipart("/api/orders/import/preview").file(file).param("mode","CREATE_ONLY"),editor,null,200);
        assertThat(p.get("totalRows").asInt()).isEqualTo(1);
        assertThat(p.get("validRows").asInt()).isEqualTo(1);
        apply(editor,p,200);
        assertThat(jdbc.queryForObject("select title from orders where code='W-1'",String.class)).isEqualTo("Кириллица из Excel");
    }
}
