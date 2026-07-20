package io.github.chrisshi.mom.mdm;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.AuditActorMissingException;
import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.mdm.application.MdmDataProbeService;
import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeEntity;
import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeMapper;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1.5 S01 CurrentActor、强制审计、更新路径和乐观锁的真实 PostgreSQL 集成测试。
 *
 * <p>该类在不注入默认测试用户的独立 Spring 上下文中运行，用于验证缺少 Actor 时写入失败、显式
 * USER/SYSTEM Actor、调用方伪造审计字段、Wrapper-only Update 和自定义 SQL 审计责任。</p>
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
class MdmAuditPostgresqlIntegrationTest {

    private static final String SCHEMA = "mom_mdm";
    private static final Duration PG_PRECISION = Duration.ofNanos(1_000);

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform")
            .withUsername("mom")
            .withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired
    private MdmDataProbeMapper mapper;

    @Autowired
    private MdmDataProbeService service;

    @Autowired
    private AuditContextExecutor executor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 注册审计集成测试的独立 PostgreSQL 连接。 */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA
                + "&tcpKeepAlive=true"
                + "&ApplicationName=mom-mdm-audit-test");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    /** 验证 INSERT 使用同一 Actor 和同一 UTC 时间。 */
    @Test
    void insertShouldForceSameActorAndTime() {
        MdmDataProbeEntity entity = asUser("user-create", () -> service.create(unique("insert"), "value"));
        assertNotNull(entity.getId());
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
        assertEquals("user-create", entity.getCreatedBy());
        assertEquals("user-create", entity.getUpdatedBy());
        assertEquals(0L, entity.getVersion());
        assertFalse(entity.getDeleted());
    }

    /** 验证调用方伪造的四个审计字段被服务端 Actor 和时间覆盖。 */
    @Test
    void forgedAuditFieldsShouldBeOverwritten() {
        MdmDataProbeEntity entity = entity(unique("forged"), "value");
        entity.setCreatedAt(Instant.EPOCH);
        entity.setUpdatedAt(Instant.EPOCH);
        entity.setCreatedBy("forged-created");
        entity.setUpdatedBy("forged-updated");
        asUser("server-actor", () -> mapper.insert(entity));

        MdmDataProbeEntity saved = mapper.selectById(entity.getId());
        assertEquals("server-actor", saved.getCreatedBy());
        assertEquals("server-actor", saved.getUpdatedBy());
        assertTrue(saved.getCreatedAt().isAfter(Instant.EPOCH));
        assertNear(saved.getCreatedAt(), saved.getUpdatedAt());
    }

    /** 验证缺少 Actor 时 fail-closed，显式 SYSTEM Actor 可以写入。 */
    @Test
    void missingActorShouldFailAndExplicitSystemShouldWrite() {
        MdmDataProbeEntity missing = entity(unique("missing"), "value");
        RuntimeException failure = assertThrows(RuntimeException.class, () -> mapper.insert(missing));
        assertTrue(hasCause(failure, AuditActorMissingException.class));

        MdmDataProbeEntity system = executor.runAsSystem(
                "mom-mdm-test-writer",
                () -> service.create(unique("system"), "value"));
        assertEquals("mom-mdm-test-writer", system.getCreatedBy());
    }

    /** 验证 updateById 不覆盖创建审计并递增版本。 */
    @Test
    void updateByIdShouldKeepCreatedAuditAndIncrementVersion() {
        MdmDataProbeEntity created = asUser("creator", () -> service.create(unique("update-id"), "before"));
        assertTrue(asUser("updater", () ->
                service.updateValue(created.getId(), created.getVersion(), "after")));

        MdmDataProbeEntity saved = mapper.selectById(created.getId());
        assertEquals("creator", saved.getCreatedBy());
        assertEquals("updater", saved.getUpdatedBy());
        assertEquals(1L, saved.getVersion());
        assertNear(created.getCreatedAt(), saved.getCreatedAt());
    }

    /** 验证 update(entity, wrapper) 能触发更新审计。 */
    @Test
    void updateEntityAndWrapperShouldFillAudit() {
        MdmDataProbeEntity created = asUser(
                "creator-wrapper",
                () -> service.create(unique("entity-wrapper"), "before"));
        assertTrue(asUser(
                "wrapper-updater",
                () -> service.updateValueByKey(created.getProbeKey(), "after")));

        MdmDataProbeEntity saved = mapper.selectById(created.getId());
        assertEquals("after", saved.getProbeValue());
        assertEquals("wrapper-updater", saved.getUpdatedBy());
    }

    /** 验证 Wrapper-only Update 被统一 Mapper 拒绝。 */
    @Test
    void wrapperOnlyUpdateShouldBeRejected() {
        assertThrows(UnsupportedOperationException.class, () -> mapper.update(
                Wrappers.<MdmDataProbeEntity>lambdaUpdate()
                        .set(MdmDataProbeEntity::getProbeValue, "forbidden")
                        .eq(MdmDataProbeEntity::getProbeKey, unique("none"))));
    }

    /** 验证批量插入中的每个实体都获得相同当前 Actor。 */
    @Test
    void batchInsertShouldFillEveryEntity() {
        MdmDataProbeEntity first = entity(unique("batch-a"), "a");
        MdmDataProbeEntity second = entity(unique("batch-b"), "b");
        asUser("batch-writer", () -> mapper.insert(List.of(first, second)));
        assertEquals("batch-writer", mapper.selectById(first.getId()).getCreatedBy());
        assertEquals("batch-writer", mapper.selectById(second.getId()).getUpdatedBy());
    }

    /** 验证自定义 SQL 显式写入 updatedAt 和 updatedBy。 */
    @Test
    void customSqlMustSupplyExplicitAudit() {
        MdmDataProbeEntity created = asUser(
                "custom-creator",
                () -> service.create(unique("custom"), "before"));
        Instant time = Instant.parse("2026-07-20T02:03:04Z");
        assertEquals(1, mapper.updateValueWithExplicitAudit(
                created.getId(), "after", time, "mom-mdm-explicit-sql"));

        MdmDataProbeEntity saved = mapper.selectById(created.getId());
        assertEquals("mom-mdm-explicit-sql", saved.getUpdatedBy());
        assertNear(time, saved.getUpdatedAt());
    }

    /** 验证两个相同版本的更新只能有一个成功。 */
    @Test
    void optimisticLockShouldAllowOnlyOneVersion() {
        MdmDataProbeEntity created = asUser(
                "optimistic-creator",
                () -> service.create(unique("optimistic"), "initial"));
        MdmDataProbeEntity first = mapper.selectById(created.getId());
        MdmDataProbeEntity stale = mapper.selectById(created.getId());

        assertTrue(asUser("first-writer", () ->
                service.updateValue(first.getId(), first.getVersion(), "first")));
        assertFalse(asUser("stale-writer", () ->
                service.updateValue(stale.getId(), stale.getVersion(), "stale")));

        MdmDataProbeEntity saved = mapper.selectById(created.getId());
        assertEquals("first", saved.getProbeValue());
        assertEquals(1L, saved.getVersion());
    }

    /** 验证逻辑删除和事务回滚仍保持原有语义。 */
    @Test
    void logicDeleteAndRollbackShouldRemainValid() {
        MdmDataProbeEntity created = asUser(
                "delete-creator",
                () -> service.create(unique("delete"), "value"));
        assertTrue(asUser("deleter", () -> service.deleteById(created.getId())));
        assertTrue(service.findByKey(created.getProbeKey()).isEmpty());

        String key = unique("rollback");
        assertThrows(IllegalStateException.class, () -> asUser("rollback-writer", () -> {
            service.createThenRollback(key, "value");
            return null;
        }));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "select count(*) from technical_data_probe where probe_key = ?",
                Long.class,
                key));
    }

    private <T> T asUser(String actorId, Supplier<T> action) {
        return executor.runAsActor(new AuditActor(
                actorId,
                ActorType.USER,
                "INTERNAL",
                "mom-admin-web",
                "sid-test",
                "corr-test"), action);
    }

    private static MdmDataProbeEntity entity(String key, String value) {
        MdmDataProbeEntity entity = new MdmDataProbeEntity();
        entity.setProbeKey(key);
        entity.setProbeValue(value);
        return entity;
    }

    private static boolean hasCause(Throwable value, Class<? extends Throwable> type) {
        for (Throwable current = value; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static void assertNear(Instant expected, Instant actual) {
        assertTrue(Duration.between(expected, actual).abs().compareTo(PG_PRECISION) <= 0);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
