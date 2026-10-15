package io.github.chrisshi.mom.qms;

import org.flywaydb.core.Flyway;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 QMS 已发布质量定义表只作为历史只读数据保留，不再暴露有效的定义文案。
 *
 * <p>该测试不会证明 QMS 仍拥有质量定义；新权威及写入能力由 MDM 集成测试覆盖。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = MomQmsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "mom.security.resource-server.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
        })
class QmsLegacySchemaPostgresqlIT {
    private static final String SCHEMA = "mom_qms";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform")
            .withUsername("mom")
            .withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=UTC");

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** 为 Flyway 和应用数据源注入隔离的 QMS Schema。 */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA
                + "&tcpKeepAlive=true&ApplicationName=mom-qms-legacy-schema-it");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    /** 验证 V4 已标记历史表并停用旧质量定义运行时文案。 */
    @Test
    void legacyQualityDefinitionMustBeRetiredAfterV4() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");

        String tableComment = jdbcTemplate.queryForObject(
                "SELECT obj_description('qms_quality_specification'::regclass)", String.class);
        assertThat(tableComment).startsWith("LEGACY READ-ONLY DATA:");

        Integer enabledLegacyMessages = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM qms_i18n_message_definition
                WHERE enabled = true
                  AND (message_key LIKE 'qms.common.%' OR message_key LIKE 'qms.spec.%')
                """, Integer.class);
        assertThat(enabledLegacyMessages).isZero();
    }
}
