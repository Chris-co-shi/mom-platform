package io.github.chrisshi.mom.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** 在真实 Auth Flyway Schema 上验证各 Owner 编码规则权限种子。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AuthApplication.class, properties = {
        "spring.main.banner-mode=off", "spring.cloud.nacos.discovery.enabled=false",
        "mom.security.resource-server.enabled=false"})
class AuthCodeRulePermissionsPostgresqlIT {
    @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=mom_auth");
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.flyway.schemas", () -> "mom_auth");
        registry.add("spring.flyway.default-schema", () -> "mom_auth");
    }
    @Autowired JdbcTemplate jdbc;
    @Test void mdmOwnsReadWriteAndSeparateGenerateAuthorities() {
        List<String> codes = jdbc.queryForList("""
                SELECT p.code FROM auth_permission p
                JOIN auth_permission_resource r ON p.resource_id=r.id
                WHERE r.domain_code='MDM' AND r.resource_code='CODE_RULE'
                  AND p.enabled=true AND p.deleted=false ORDER BY p.code
                """, String.class);
        assertThat(codes).containsExactly("mdm:code-rule:generate", "mdm:code-rule:read", "mdm:code-rule:write");
    }
    @Test void wmsOwnsReadWriteAndSeparateGenerateAuthorities() {
        List<String> codes = jdbc.queryForList("""
                SELECT p.code FROM auth_permission p
                JOIN auth_permission_resource r ON p.resource_id=r.id
                WHERE r.domain_code='WMS' AND r.resource_code='CODE_RULE'
                  AND p.enabled=true AND p.deleted=false ORDER BY p.code
                """, String.class);
        assertThat(codes).containsExactly("wms:code-rule:generate", "wms:code-rule:read", "wms:code-rule:write");
    }
}
