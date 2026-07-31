package io.github.chrisshi.mom.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = MomIntegrationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "management.health.redis.enabled=false",
                "mom.technical-probe.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
        })
class IntegrationTechnicalProbeRetirementPostgresqlIT {

    private static final String SCHEMA = "mom_integration";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform")
            .withUsername("mom")
            .withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA
                + "&tcpKeepAlive=true&ApplicationName=mom-integration-retirement-test");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @Test
    void migrationsShouldRetireMessageAndSeataTechnicalTables() {
        assertEquals(5, jdbcTemplate.queryForObject(
                "select max(version::integer) from flyway_schema_history where success = true",
                Integer.class));
        assertEquals(0L, jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema = ?
                   and table_name in (
                     'technical_message_receipt', 'mom_inbox_event',
                     'technical_seata_at_participant', 'undo_log')
                """, Long.class, SCHEMA));
    }
}
