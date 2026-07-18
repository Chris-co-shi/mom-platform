package io.github.chrisshi.mom.mdm;

import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.mdm.application.MdmDataProbeService;
import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeEntity;
import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MDM 数据访问真实 PostgreSQL 集成测试。
 *
 * <p>测试使用 PostgreSQL 17.7 官方镜像，并把数据库服务端默认时区设置为 Asia/Tokyo，以确认 MOM
 * 连接池的 UTC 初始化语句真实生效。测试同时验证 Flyway Schema、MyBatis-Plus 审计填充、乐观锁和
 * Spring 事务回滚，不使用 H2 模拟 PostgreSQL 行为。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = MomMdmApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
        })
@Import(MdmPostgresqlDataIntegrationTest.TestActorConfiguration.class)
class MdmPostgresqlDataIntegrationTest {

    private static final String SCHEMA = "mom_mdm";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform")
            .withUsername("mom")
            .withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private MdmDataProbeMapper mapper;

    @Autowired
    private MdmDataProbeService service;

    /**
     * 将 Testcontainers 动态端口和凭证注入 Spring Boot 数据源。
     */
    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    /**
     * 验证 Flyway 创建独立 Schema、执行迁移并把应用数据库会话统一为 UTC。
     */
    @Test
    void flywayShouldCreateSchemaAndApplicationSessionShouldUseUtc() {
        assertEquals(SCHEMA, jdbcTemplate.queryForObject(
                "select current_schema()",
                String.class));
        assertEquals("UTC", jdbcTemplate.queryForObject(
                "show timezone",
                String.class));
        assertTrue(flyway.info().applied().length >= 1);
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Long.class));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = ? and table_name = 'technical_data_probe'",
                Long.class,
                SCHEMA));
    }

    /**
     * 验证 MyBatis-Plus 插入后回填主键、UTC 审计字段、操作主体和初始版本号。
     */
    @Test
    void mybatisPlusShouldFillAuditFieldsAndGeneratedId() {
        MdmDataProbeEntity entity = service.create(
                uniqueKey("audit"),
                "created-value");

        assertNotNull(entity.getId());
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertEquals("p01-s04-test-actor", entity.getCreatedBy());
        assertEquals("p01-s04-test-actor", entity.getUpdatedBy());
        assertEquals(0L, entity.getVersion());

        MdmDataProbeEntity persisted = mapper.selectById(entity.getId());
        assertEquals(entity.getProbeKey(), persisted.getProbeKey());
        assertEquals(entity.getCreatedAt(), persisted.getCreatedAt());
        assertEquals("p01-s04-test-actor", persisted.getCreatedBy());
    }

    /**
     * 验证相同版本只能成功更新一次，过期版本不会覆盖其他事务已经提交的数据。
     */
    @Test
    void optimisticLockShouldRejectStaleVersion() {
        MdmDataProbeEntity created = service.create(
                uniqueKey("optimistic"),
                "initial-value");
        MdmDataProbeEntity firstSnapshot = mapper.selectById(created.getId());
        MdmDataProbeEntity staleSnapshot = mapper.selectById(created.getId());

        assertTrue(service.updateValue(
                firstSnapshot.getId(),
                firstSnapshot.getVersion(),
                "first-update"));
        assertFalse(service.updateValue(
                staleSnapshot.getId(),
                staleSnapshot.getVersion(),
                "stale-update"));

        MdmDataProbeEntity persisted = mapper.selectById(created.getId());
        assertEquals("first-update", persisted.getProbeValue());
        assertEquals(1L, persisted.getVersion());
        assertEquals("p01-s04-test-actor", persisted.getUpdatedBy());
    }

    /**
     * 验证运行时异常会回滚已经执行的 INSERT，数据库中不留下半成品记录。
     */
    @Test
    void transactionShouldRollbackInsertOnRuntimeException() {
        String probeKey = uniqueKey("rollback");

        assertThrows(IllegalStateException.class, () ->
                service.createThenRollback(probeKey, "must-not-persist"));

        assertTrue(service.findByKey(probeKey).isEmpty());
    }

    /**
     * 为并行或重复执行的测试生成互不冲突的唯一验证键。
     */
    private static String uniqueKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /**
     * 测试环境提供稳定主体，用于验证审计抽象与数据模块之间的依赖方向。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestActorConfiguration {

        /**
         * 提供固定测试主体，生产环境后续由安全模块实现同一接口。
         *
         * @return 固定主体提供器
         */
        @Bean
        CurrentActorProvider testCurrentActorProvider() {
            return () -> Optional.of("p01-s04-test-actor");
        }
    }
}
