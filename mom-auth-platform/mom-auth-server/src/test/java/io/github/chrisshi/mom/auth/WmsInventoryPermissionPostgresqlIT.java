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

import static org.assertj.core.api.Assertions.assertThat;

/** 真实 Auth Flyway 验证 WMS 读权限及管理员授权种子。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AuthApplication.class, properties = {
        "spring.main.banner-mode=off", "spring.cloud.nacos.discovery.enabled=false",
        "mom.security.resource-server.enabled=false"})
class WmsInventoryPermissionPostgresqlIT {
    @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=mom_auth");
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.flyway.schemas", () -> "mom_auth");
        registry.add("spring.flyway.default-schema", () -> "mom_auth");
    }
    @Autowired JdbcTemplate jdbc;
    @Test void permissionIsOwnedByWmsAndGrantedToPlatformAdmin() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM auth_permission p JOIN auth_permission_resource r ON r.id=p.resource_id
                 JOIN auth_role_permission rp ON rp.permission_id=p.id
                 WHERE p.code='wms:inventory:read' AND p.action_code='READ'
                   AND r.domain_code='WMS' AND r.resource_code='INVENTORY'
                   AND rp.role_id='1000000000000000002' AND p.enabled AND NOT p.deleted
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM auth_permission p JOIN auth_permission_resource r ON r.id=p.resource_id
                 JOIN auth_role_permission rp ON rp.permission_id=p.id
                 WHERE p.code IN ('wms:opening:read','wms:opening:write','wms:opening:post','wms:opening:activate')
                   AND r.domain_code='WMS' AND r.resource_code='OPENING_INVENTORY'
                   AND rp.role_id='1000000000000000002' AND p.enabled AND NOT p.deleted
                """, Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM auth_permission p JOIN auth_permission_resource r ON r.id=p.resource_id
                 JOIN auth_role_permission rp ON rp.permission_id=p.id
                 WHERE p.code IN ('wms:inventory:freeze','wms:inventory:unfreeze')
                   AND r.domain_code='WMS' AND r.resource_code='INVENTORY_FREEZE'
                   AND rp.role_id='1000000000000000002' AND p.enabled AND NOT p.deleted
                """, Integer.class)).isEqualTo(2);
    }
}
