package ru.mirea.youdo;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MigrationIntegrationTest {
    @Test void upgradesPopulatedLegacyBaseWithoutLosingIdsMoneyDatesOrLinks() throws Exception {
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:55434/youdo_test");
        String user=System.getenv().getOrDefault("TEST_DB_USER","youdo_test");
        String password=System.getenv().getOrDefault("TEST_DB_PASSWORD","isolated-test-password");
        String schema="migration_"+UUID.randomUUID().toString().replace("-","");
        var first=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("1").load();
        first.migrate();
        try (var connection=DriverManager.getConnection(url,user,password); var sql=connection.createStatement()) {
            sql.execute("SET search_path TO "+schema);
            sql.execute("insert into users(id,name,email,role,rating,registered_at) values (41,'Заказчик','customer@example.org','CUSTOMER',4.50,'2026-01-01'),(75,'Исполнитель','freelancer@example.org','FREELANCER',4.75,'2026-01-02')");
            sql.execute("insert into orders(id,title,category,budget,deadline,customer_id,freelancer_id,created_at) values (99,'Кириллица','DESIGN',12345.67,'2026-12-31',41,75,'2026-02-03 04:05:06'),(100,'Без исполнителя','OTHER',99.99,'2026-01-01',41,null,'2026-01-02 00:00:00')");
            var full=Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load();
            assertThat(full.migrate().migrationsExecuted).isEqualTo(3);
            assertThat(full.validateWithResult().validationSuccessful).isTrue();
            try (var rows=sql.executeQuery("select o.*,c.code as category_code from orders o join categories c on c.id=o.category_id where o.id=99")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("public_id",UUID.class)).isNotNull();
                assertThat(rows.getString("code")).isEqualTo("LEGACY-99");
                assertThat(rows.getString("category_code")).isEqualTo("DESIGN");
                assertThat(rows.getBigDecimal("budget")).isEqualByComparingTo("12345.67");
                assertThat(rows.getLong("customer_id")).isEqualTo(41);
                assertThat(rows.getLong("freelancer_id")).isEqualTo(75);
                assertThat(rows.getTimestamp("created_at").toInstant().toString()).isEqualTo("2026-02-03T04:05:06Z");
                assertThat(rows.getLong("version")).isZero();
            }
            try (var rows=sql.executeQuery("select freelancer_id from orders where id=100")) {
                rows.next(); assertThat(rows.getObject(1)).isNull();
            }
            assertThat(full.migrate().migrationsExecuted).isZero();
        } finally {
            try (var connection=DriverManager.getConnection(url,user,password); var sql=connection.createStatement()) {
                sql.execute("DROP SCHEMA "+schema+" CASCADE");
            }
        }
    }
}
