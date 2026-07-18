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

import java.time.Duration;
import java.time.Instant;
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
 * 连接池的 UTC 初始化语句真实生效。测试同时验证 Flyway 增量迁移、String 主键、Lombok 访问器、
 * MyBatis-Plus 审计填充、逻辑删除、乐观锁和 Spring 事务回滚，不使用 H2 模拟 PostgreSQL 行为。</p>
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
    private static final Duration POSTGRESQL_TIMESTAMP_TOLERANCE = Duration.ofNanos(1_000);

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
     * 验证 Flyway 顺序执行 V1、V2，最终形成 varchar(19) 主键、逻辑删除列和 UTC 应用会话。
     */
    @Test
    void flywayShouldMigrateStringIdLogicDeleteAndUseUtcSession() {
        assertEquals(SCHEMA, jdbcTemplate.queryForObject(
                "select current_schema()",
                String.class));
        assertEquals("UTC", jdbcTemplate.queryForObject(
                "show timezone",
                String.class));
        assertTrue(flyway.info().applied().length >= 2);
        assertEquals(2L, jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history "
                        + "where success = true and version in ('1', '2')",
                Long.class));
        assertEquals("character varying", jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = ? and table_name = 'technical_data_probe' and column_name = 'id'",
                String.class,
                SCHEMA));
        assertEquals(19, jdbcTemplate.queryForObject(
                "select character_maximum_length from information_schema.columns "
                        + "where table_schema = ? and table_name = 'technical_data_probe' and column_name = 'id'",
                Integer.class,
                SCHEMA));
        assertEquals("boolean", jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = ? and table_name = 'technical_data_probe' and column_name = 'deleted'",
                String.class,
                SCHEMA));
    }

    /**
     * 验证 MyBatis-Plus 为 String 字段生成数字字符串主键，并填充审计、版本及未删除状态。
     */
    @Test
    void mybatisPlusShouldGenerateStringIdAndFillBaseFields() {
        MdmDataProbeEntity entity = service.create(
                uniqueKey("audit"),
                "created-value");

        assertNotNull(entity.getId());
        assertTrue(entity.getId().matches("[0-9]{1,19}"));
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertEquals("p01-s04-test-actor", entity.getCreatedBy());
        assertEquals("p01-s04-test-actor", entity.getUpdatedBy());
        assertEquals(0L, entity.getVersion());
        assertFalse(entity.getDeleted());

        MdmDataProbeEntity persisted = mapper.selectById(entity.getId());
        assertEquals(entity.getId(), persisted.getId());
        assertEquals(entity.getProbeKey(), persisted.getProbeKey());
        assertWithinPostgresqlTimestampPrecision(
                entity.getCreatedAt(),
                persisted.getCreatedAt());
        assertWithinPostgresqlTimestampPrecision(
                entity.getUpdatedAt(),
                persisted.getUpdatedAt());
        assertEquals("p01-s04-test-actor", persisted.getCreatedBy());
        assertFalse(persisted.getDeleted());
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
     * 验证 deleteById 只更新删除标记，并让普通查询自动排除已删除记录。
     */
    @Test
    void logicDeleteShouldHideRowWithoutPhysicalDeletion() {
        String probeKey = uniqueKey("logic-delete");
        MdmDataProbeEntity created = service.create(probeKey, "delete-me");

        assertTrue(service.deleteById(created.getId()));
        assertTrue(service.findByKey(probeKey).isEmpty());
        assertFalse(service.deleteById(created.getId()));

        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from technical_data_probe where id = ?",
                Long.class,
                created.getId()));
        assertTrue(jdbcTemplate.queryForObject(
                "select deleted from technical_data_probe where id = ?",
                Boolean.class,
                created.getId()));
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
     * 按 PostgreSQL {@code timestamptz} 的微秒精度比较 Java Instant。
     *
     * <p>Java Instant 支持纳秒，而 PostgreSQL 会把时间舍入到微秒。测试允许不超过一微秒的差异，
     * 但仍能发现时区错误或明显的审计时间偏移。</p>
     */
    private static void assertWithinPostgresqlTimestampPrecision(
            Instant expected,
            Instant actual) {
        Duration difference = Duration.between(expected, actual).abs();
        assertTrue(
                difference.compareTo(POSTGRESQL_TIMESTAMP_TOLERANCE) <= 0,
                () -> "PostgreSQL 时间精度差异超过一微秒：" + difference);
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
