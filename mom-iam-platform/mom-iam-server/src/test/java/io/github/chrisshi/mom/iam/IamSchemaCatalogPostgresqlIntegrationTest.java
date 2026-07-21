package io.github.chrisshi.mom.iam;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IAM Schema、中文注释、官方协议表和初始化目录的真实 PostgreSQL 验证。 */
class IamSchemaCatalogPostgresqlIntegrationTest extends AbstractIamPostgresqlIntegrationTest {
    private static final List<String> TABLES = List.of(
            "iam_user", "iam_internal_user_profile", "iam_external_user_binding",
            "iam_role", "iam_permission", "iam_user_role", "iam_role_permission",
            "iam_user_application", "iam_user_factory_scope", "iam_oauth_client_policy",
            "oauth2_registered_client", "oauth2_authorization", "oauth2_authorization_consent",
            "iam_user_session", "iam_refresh_token", "iam_security_audit_event");

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void flywayDatasourceSchemaAndUtcMustMatchGovernance() {
        HikariDataSource hikari = assertInstanceOf(HikariDataSource.class, dataSource);
        assertEquals("mom-iam-hikari", hikari.getPoolName());
        assertEquals(1, hikari.getMinimumIdle());
        assertEquals(5, hikari.getMaximumPoolSize());
        assertEquals("SET TIME ZONE 'UTC'", hikari.getConnectionInitSql());
        assertEquals(SCHEMA, jdbc.queryForObject("select current_schema()", String.class));
        assertEquals("UTC", jdbc.queryForObject("show timezone", String.class));
        assertEquals(APPLICATION_NAME, jdbc.queryForObject(
                "select application_name from pg_stat_activity where pid=pg_backend_pid()", String.class));
        assertEquals(7L, jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success=true and version in ('1','2','3','4','5','6','7')",
                Long.class));
        long before = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success=true", Long.class);
        assertTrue(flyway.getConfiguration().isCleanDisabled());
        flyway.validate();
        flyway.migrate();
        assertEquals(before, jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success=true", Long.class));
    }

    @Test
    void formalTablesColumnsTypesAndCommentsMustBeComplete() {
        assertEquals(Set.copyOf(TABLES), Set.copyOf(jdbc.queryForList(
                "select tablename from pg_tables where schemaname=? and tablename <> 'flyway_schema_history'",
                String.class, SCHEMA)));
        List<Map<String,Object>> tableComments = jdbc.queryForList("""
                SELECT c.relname table_name, obj_description(c.oid,'pg_class') comment
                  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                 WHERE n.nspname=? AND c.relkind='r'
                """, SCHEMA);
        List<Map<String,Object>> badTables = tableComments.stream()
                .filter(row -> TABLES.contains(String.valueOf(row.get("table_name"))))
                .filter(row -> row.get("comment") == null || String.valueOf(row.get("comment")).isBlank())
                .toList();
        assertTrue(badTables.isEmpty(), () -> "数据库表注释缺失：" + badTables);
        List<Map<String,Object>> columnComments = jdbc.queryForList("""
                SELECT c.relname table_name, a.attname column_name,
                       col_description(c.oid,a.attnum) comment
                  FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                  JOIN pg_attribute a ON a.attrelid=c.oid
                 WHERE n.nspname=? AND a.attnum>0 AND NOT a.attisdropped
                """, SCHEMA);
        Set<String> invalid = Set.of("", "编码", "状态", "时间", "版本", "备注", "用户ID");
        List<Map<String,Object>> badColumns = columnComments.stream()
                .filter(row -> TABLES.contains(String.valueOf(row.get("table_name"))))
                .filter(row -> row.get("comment") == null
                        || invalid.contains(String.valueOf(row.get("comment")).trim()))
                .toList();
        assertTrue(badColumns.isEmpty(), () -> "数据库字段注释缺失或无效：" + badColumns);
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema=? AND table_name ~ '^iam_'
                   AND column_name='id'
                   AND NOT(data_type='character varying' AND character_maximum_length=19)
                """, Long.class, SCHEMA));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema=? AND column_name ~ '_at$'
                   AND data_type <> 'timestamp with time zone'
                """, Long.class, SCHEMA));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint con
                  JOIN pg_namespace child_ns ON child_ns.oid=con.connamespace
                  JOIN pg_class parent_table ON parent_table.oid=con.confrelid
                  JOIN pg_namespace parent_ns ON parent_ns.oid=parent_table.relnamespace
                 WHERE con.contype='f' AND child_ns.nspname=? AND parent_ns.nspname<>?
                """, Long.class, SCHEMA, SCHEMA));
        assertEquals(128, jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                 WHERE table_schema=? AND table_name='iam_user' AND column_name='created_by'
                """, Integer.class, SCHEMA));
        String activeIndex = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                 WHERE schemaname=? AND indexname='uk_iam_refresh_token_one_active_per_session'
                """, String.class, SCHEMA);
        assertTrue(activeIndex.contains("ACTIVE"));
        assertFalse(jdbc.queryForObject("""
                SELECT string_agg(pg_get_constraintdef(c.oid),' ') FROM pg_constraint c
                 JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname=?
                """, String.class, SCHEMA).contains("ALL_FACTORIES"));
    }

    @Test
    void springSecurity71OfficialJdbcSchemaMustBeCompatible() {
        assertTrue(columns("oauth2_registered_client").containsAll(Set.of(
                "id", "client_id", "client_id_issued_at", "client_secret",
                "client_secret_expires_at", "client_name", "client_authentication_methods",
                "authorization_grant_types", "redirect_uris", "post_logout_redirect_uris",
                "scopes", "client_settings", "token_settings")));
        assertTrue(columns("oauth2_authorization").containsAll(Set.of(
                "id", "registered_client_id", "principal_name", "authorization_grant_type",
                "attributes", "authorization_code_value", "access_token_value",
                "oidc_id_token_value", "refresh_token_value", "user_code_value", "device_code_value")));
        assertEquals(Set.of("registered_client_id", "principal_name", "authorities"),
                columns("oauth2_authorization_consent"));
        JdbcRegisteredClientRepository clients = new JdbcRegisteredClientRepository(jdbc);
        assertNull(clients.findByClientId("not-seeded-in-s02"));
        assertNull(new JdbcOAuth2AuthorizationService(jdbc, clients).findById("not-created-in-s02"));
    }

    @Test
    void seedRolesPermissionsMappingsAndClientPoliciesMustBeFrozen() {
        assertEquals(Set.of("PLATFORM_ADMIN", "IAM_ADMIN", "SECURITY_AUDITOR"),
                Set.copyOf(jdbc.queryForList(
                        "select code from iam_role where built_in=true and deleted=false", String.class)));
        assertEquals(28L, jdbc.queryForObject(
                "select count(*) from iam_permission where built_in=true and deleted=false", Long.class));
        assertEquals(28L, jdbc.queryForObject("""
                SELECT count(*) FROM iam_role_permission rp JOIN iam_role r ON r.id=rp.role_id
                 WHERE r.code='PLATFORM_ADMIN'
                """, Long.class));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM iam_role_permission rp
                  JOIN iam_role r ON r.id=rp.role_id JOIN iam_permission p ON p.id=rp.permission_id
                 WHERE r.code='SECURITY_AUDITOR' AND p.action_code<>'read'
                """, Long.class));
        assertEquals(59L, jdbc.queryForObject("select count(*) from iam_role_permission", Long.class));
        assertEquals(4L, jdbc.queryForObject("select count(*) from iam_oauth_client_policy", Long.class));
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM iam_oauth_client_policy
                 WHERE client_id='mom-mobile-pda' AND application_code='MOM_MOBILE_PDA'
                   AND channel='MOBILE' AND allowed_user_type='INTERNAL' AND mobile_access_required=true
                """, Long.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from oauth2_registered_client", Long.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from iam_user", Long.class));
    }

    private Set<String> columns(String table) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema=? AND table_name=?
                """, String.class, SCHEMA, table));
    }
}
