package io.github.chrisshi.mom.auth;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.auth.application.model.AuthPageParams.PermissionPageParams;
import io.github.chrisshi.mom.auth.infrastructure.query.PermissionCatalogQueryMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 以 MySQL 启动完整 Auth 服务，验证基线种子、连接会话及权限目录查询。 */
@Testcontainers
@SpringBootTest(classes = AuthApplication.class, properties = {
    "MOM_DATABASE_VENDOR=mysql", "spring.cloud.nacos.discovery.enabled=false",
    "mom.security.resource-server.enabled=false"
})
class AuthMysqlIT {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("mom_auth");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        // 模拟已部署 V1 的实例，由真实服务启动增量执行 V2 种子。
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration-mysql/auth")
            .target("1")
            .load()
            .migrate();
        registry.add("POSTGRES_URL", () -> "jdbc:postgresql://127.0.0.1:1/unexpected_postgresql");
        registry.add("MYSQL_URL", MYSQL::getJdbcUrl);
        registry.add("MYSQL_USERNAME", MYSQL::getUsername);
        registry.add("MYSQL_PASSWORD", MYSQL::getPassword);
    }

    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PermissionCatalogQueryMapper catalog;

    @Test
    void startsWithOneDataSourceAndSearchesCurrentPermissionSeeds() {
        assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("mom_auth");
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history ORDER BY installed_rank",
            String.class)).containsExactly("1", "2");
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");
        assertThat(jdbc.queryForObject("SELECT @@session.transaction_isolation", String.class)).isEqualTo("READ-COMMITTED");
        var page = catalog.searchCatalog(new Page<>(1, 1),
            new PermissionPageParams("WMS", null, "WMS:CODE-RULE:GENERATE", true));
        assertThat(page.getTotal()).isEqualTo(1);
        var permission = page.getRecords().getFirst();
        assertThat(permission.getCode()).isEqualTo("wms:code-rule:generate");
        assertThat(permission.getCreatedAt()).isEqualTo(Instant.ofEpochSecond(
            jdbc.queryForObject("SELECT FLOOR(UNIX_TIMESTAMP(created_at)) FROM auth_permission WHERE id=?",
                Long.class, permission.getId())).plusNanos(jdbc.queryForObject(
            "SELECT MICROSECOND(created_at) FROM auth_permission WHERE id=?", Long.class, permission.getId()) * 1000));
    }
}
