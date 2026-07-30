package io.github.chrisshi.mom.system;

import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.CreateMessageCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.CreateResourceCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.PublishCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.RollbackCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.StatusCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.UpdateMessageCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationService;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dynamic I18n 的真实 PostgreSQL 17.7、V1+V2+V3、事务、并发、JSONB 与不可变 Release 集成测试。
 *
 * <p>测试使用独立容器和 mom_system Schema，覆盖 Parameter/Dictionary 向前兼容、三张 I18n 表、同
 * Schema FK、Draft 与 Published 隔离、两 Locale 原子发布、fallback、No-op、行锁并发、Runtime Kill
 * Switch、回滚新版本与历史。Docker 不可用时 Testcontainers 明确跳过，不能描述为专项成功。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = MomSystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "mom.security.resource-server.enabled=false"
        })
@Import(SystemParameterPostgresqlIT.TestActorConfiguration.class)
class SystemI18nPostgresqlIT {
    private static final String SCHEMA = "mom_system";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform").withUsername("mom").withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Flyway flyway;
    @Autowired
    private SystemI18nApplicationService service;

    /** 注入动态 PostgreSQL 地址和单一 mom_system search_path。 */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA + "&tcpKeepAlive=true&ApplicationName=mom-system-server");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @BeforeEach
    void cleanTables() {
        jdbc.update("TRUNCATE TABLE system_i18n_release, system_i18n_message, system_i18n_resource, "
                + "system_dictionary_item, system_dictionary, system_parameter");
    }

    @Test
    void v3MustCreateThreeConstrainedJsonbTablesInSameSchema() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success and version in ('1','2','3')",
                Long.class)).isEqualTo(3L);
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema=? AND table_name LIKE 'system_i18n_%'
                 ORDER BY table_name
                """, String.class, SCHEMA)).containsExactly(
                "system_i18n_message", "system_i18n_release", "system_i18n_resource");
        assertThat(jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                 WHERE table_schema=? AND table_name='system_i18n_release' AND column_name='messages_json'
                """, String.class, SCHEMA)).isEqualTo("jsonb");
        assertThat(jdbc.queryForObject("""
                SELECT count(*)
                  FROM information_schema.table_constraints tc
                  JOIN information_schema.constraint_column_usage ccu
                    ON ccu.constraint_name=tc.constraint_name AND ccu.constraint_schema=tc.constraint_schema
                 WHERE tc.table_schema=? AND tc.constraint_type='FOREIGN KEY' AND ccu.table_schema<>?
                """, Long.class, SCHEMA, SCHEMA)).isZero();
    }

    @Test
    void databaseMustEnforceUniqueChecksForeignKeysAndReleaseImmutability() {
        var resource = resource("mom-web", "common");
        assertThatThrownBy(() -> resource("mom-web", "common"))
                .isInstanceOf(SystemI18nException.Conflict.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO system_i18n_message (
                    id, resource_id, message_key, locale, message_value, enabled, version,
                    created_by, created_at, updated_by, updated_at)
                VALUES ('orphan', 'missing', 'hello', 'zh-CN', 'x', true, 0, 't', now(), 't', now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
        service.createMessage(resource.id(), message("hello", "zh-CN", "你好", true));
        service.publish(resource.id(), new PublishCommand(resource.version(), "initial"));
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE system_i18n_release SET change_note='mutated' WHERE resource_id=?
                """, resource.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM system_i18n_release WHERE resource_id=?", resource.id()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void draftPublishFallbackRuntimeKillSwitchRollbackAndHistoryMustRemainConsistent() {
        var resource = resource("mom-web", "common");
        var zhHello = service.createMessage(resource.id(),
                message("hello", "zh-CN", "你好 {username}", true));
        service.createMessage(resource.id(), message("hello", "en-US", "Hello {username}", true));
        var zhOnly = service.createMessage(resource.id(), message("only.zh", "zh-CN", "仅中文", true));

        var first = service.publish(resource.id(), new PublishCommand(resource.version(), "initial"));
        assertThat(first.releaseVersion()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM system_i18n_release WHERE resource_id=? AND release_version=1
                """, Long.class, resource.id())).isEqualTo(2L);
        var english = service.runtime("mom-web", "common", "en-US");
        assertThat(english.messages()).containsEntry("only.zh", "仅中文");
        assertThat(english.fallbackCount()).isEqualTo(1);

        var currentResource = service.getResource(resource.id());
        long unchangedVersion = currentResource.version();
        assertThatThrownBy(() -> service.publish(resource.id(),
                new PublishCommand(unchangedVersion, "same")))
                .isInstanceOf(SystemI18nException.Conflict.class);

        service.updateMessage(resource.id(), zhOnly.id(),
                new UpdateMessageCommand("草稿已改", null, zhOnly.version()));
        assertThat(service.runtime("mom-web", "common", "zh-CN").messages())
                .containsEntry("only.zh", "仅中文");

        service.updateMessage(resource.id(), zhHello.id(),
                new UpdateMessageCommand("您好 {username}", null, zhHello.version()));
        currentResource = service.getResource(resource.id());
        var second = service.publish(resource.id(), new PublishCommand(currentResource.version(), "changed"));
        assertThat(second.releaseVersion()).isEqualTo(2L);

        currentResource = service.getResource(resource.id());
        var rollback = service.rollback(resource.id(),
                new RollbackCommand(1L, currentResource.version(), "restore v1"));
        assertThat(rollback.releaseVersion()).isEqualTo(3L);
        assertThat(rollback.sourceReleaseVersion()).isEqualTo(1L);
        assertThat(service.runtime("mom-web", "common", "zh-CN").messages())
                .containsEntry("hello", "你好 {username}")
                .containsEntry("only.zh", "仅中文");
        assertThat(service.getMessage(resource.id(), zhOnly.id()).messageValue()).isEqualTo("草稿已改");
        assertThat(service.releaseHistory(resource.id(), 0, 20).items())
                .extracting(item -> item.releaseVersion()).containsExactly(3L, 2L, 1L);

        currentResource = service.getResource(resource.id());
        var disabled = service.changeResourceStatus(resource.id(),
                new StatusCommand(false, currentResource.version()));
        assertThatThrownBy(() -> service.runtime("mom-web", "common", "zh-CN"))
                .isInstanceOf(SystemI18nException.NotFound.class);
        service.changeResourceStatus(resource.id(), new StatusCommand(true, disabled.version()));
        assertThat(service.runtime("mom-web", "common", "zh-CN").releaseVersion()).isEqualTo(3L);
    }

    @Test
    void invalidPublishMustBeAtomicAndConcurrentPublishMustSerializeByResourceRow() throws Exception {
        var invalid = resource("mom-web", "invalid");
        service.createMessage(invalid.id(), message("hello", "zh-CN", "你好 {username}", true));
        service.createMessage(invalid.id(), message("hello", "en-US", "Hello {count}", true));
        assertThatThrownBy(() -> service.publish(invalid.id(), new PublishCommand(invalid.version(), "bad")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM system_i18n_release WHERE resource_id=?", Long.class, invalid.id())).isZero();
        assertThat(service.getResource(invalid.id()).publishedVersion()).isNull();

        var concurrent = resource("mom-web", "concurrent");
        service.createMessage(concurrent.id(), message("hello", "zh-CN", "你好", true));
        CountDownLatch start = new CountDownLatch(1);
        List<Object> outcomes = java.util.Collections.synchronizedList(new ArrayList<>());
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        outcomes.add(service.publish(concurrent.id(),
                                new PublishCommand(concurrent.version(), "concurrent")));
                    } catch (Exception exception) {
                        outcomes.add(exception);
                    }
                });
            }
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        assertThat(outcomes.stream().filter(value -> !(value instanceof Exception)).count()).isEqualTo(1L);
        assertThat(outcomes.stream().filter(SystemI18nException.StaleVersion.class::isInstance).count())
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM system_i18n_release WHERE resource_id=?", Long.class, concurrent.id()))
                .isEqualTo(2L);
    }

    private io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.ResourceView resource(
            String applicationCode, String resourceCode) {
        return service.createResource(new CreateResourceCommand(
                applicationCode, resourceCode, "Common", "zh-CN", null, true));
    }

    private static CreateMessageCommand message(String key, String locale, String value, boolean enabled) {
        return new CreateMessageCommand(key, locale, value, null, enabled);
    }
}
