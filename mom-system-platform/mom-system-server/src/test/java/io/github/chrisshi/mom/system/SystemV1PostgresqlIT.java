package io.github.chrisshi.mom.system;

import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.system.application.SystemV1Exception;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryApplication;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.ChangeStatus;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.CreateItem;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.CreateType;
import io.github.chrisshi.mom.system.application.i18n.I18nApplication;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.ChangeLocaleStatus;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.CreateLocale;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.CreateMessage;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.SaveTranslation;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.UpdateMessage;
import io.github.chrisshi.mom.system.application.i18n.SupportedLocaleApplication;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nRuntimeProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * System V1 在真实 PostgreSQL 17 上的 Migration、约束、事务、回退和 Runtime 聚合验收。
 *
 * <p>容器使用独立 mom_system Schema，完整执行不可修改的 V1～V9 和新增 V10。测试只清理本次动态数据，
 * 保留 V10 初始 Locale/错误 Translation；不启动 Nacos、Redis、RocketMQ 或 Seata。Docker 不可用时测试
 * 明确跳过，不能把跳过描述为 PostgreSQL 验收成功。</p>
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
@Import(SystemV1PostgresqlIT.TestActorConfiguration.class)
class SystemV1PostgresqlIT {
    private static final String SCHEMA = "mom_system";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform")
            .withUsername("mom")
            .withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Flyway flyway;
    @Autowired
    private DictionaryApplication dictionary;
    @Autowired
    private SupportedLocaleApplication locales;
    @Autowired
    private I18nApplication i18n;
    @Autowired
    private SystemI18nRuntimeProvider runtime;

    /** 注入动态 PostgreSQL 地址和受控 mom_system search_path。 */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA
                + "&tcpKeepAlive=true&ApplicationName=mom-system-server");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    /** 每例恢复 V10 初始默认 Locale 并删除测试创建的数据。 */
    @BeforeEach
    void resetCurrentV1Data() {
        jdbc.update("DELETE FROM system_i18n_translation WHERE id NOT LIKE '93000000000000000%'");
        jdbc.update("DELETE FROM system_i18n_message_definition WHERE id NOT LIKE '92000000000000000%'");
        jdbc.update("DELETE FROM system_supported_locale WHERE id NOT LIKE '910000000000000000%'");
        jdbc.update("UPDATE system_supported_locale SET is_default=false, enabled=true WHERE is_default=true");
        jdbc.update("UPDATE system_supported_locale SET is_default=true WHERE locale_code='zh-CN'");
        jdbc.update("TRUNCATE TABLE system_dictionary_item, system_dictionary");
    }

    /** 历史 Migration 保持存在，V10 新表无物理外键且种子默认 Locale 唯一。 */
    @Test
    void migrationMustPreserveHistoryAndCreateOnlyCurrentV1Tables() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("10");
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema=? AND table_name IN (
                   'system_parameter','system_application','system_user_preference',
                   'system_supported_locale','system_i18n_message_definition','system_i18n_translation')
                 ORDER BY table_name
                """, String.class, SCHEMA)).containsExactly(
                "system_application", "system_i18n_message_definition", "system_i18n_translation",
                "system_parameter", "system_supported_locale", "system_user_preference");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                 WHERE table_schema=? AND constraint_type='FOREIGN KEY'
                   AND table_name IN ('system_supported_locale','system_i18n_message_definition','system_i18n_translation')
                """, Long.class, SCHEMA)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM system_supported_locale WHERE is_default AND enabled", Long.class))
                .isEqualTo(1L);
    }

    /** 数据库唯一约束与 Application 禁用语义共同保护 Dictionary。 */
    @Test
    void dictionaryMustEnforceUniqueKeysAndPreserveDisabledHistory() {
        var type = dictionary.createType(new CreateType("system.common.state", "状态", true, null));
        assertThatThrownBy(() -> dictionary.createType(
                new CreateType("system.common.state", "重复", true, null)))
                .isInstanceOf(SystemV1Exception.Conflict.class);
        var item = dictionary.createItem(type.id(), new CreateItem("ready", "就绪", 10, true, null));
        assertThatThrownBy(() -> dictionary.createItem(
                type.id(), new CreateItem("ready", "重复", 20, true, null)))
                .isInstanceOf(SystemV1Exception.Conflict.class);

        dictionary.changeItemStatus(type.id(), item.id(), new ChangeStatus(false, item.version()));
        assertThat(dictionary.activeItems(type.code())).isEmpty();
        assertThat(dictionary.resolveItem(type.code(), "ready").value()).isEqualTo("就绪");
        assertThat(dictionary.resolveItem(type.code(), "ready").selectable()).isFalse();
    }

    /** 平台始终只能有一个启用默认 Locale，默认 Locale 不允许直接禁用。 */
    @Test
    void localeMustKeepSingleEnabledDefaultAndFallbackDisabledRequest() {
        var cs = locales.create(new CreateLocale("cs-CZ", "捷克语", "Čeština", true, 30));
        locales.makeDefault(cs.id(), cs.version());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM system_supported_locale WHERE is_default AND enabled", Long.class))
                .isEqualTo(1L);
        var current = locales.list().stream().filter(locale -> locale.id().equals(cs.id())).findFirst().orElseThrow();
        assertThatThrownBy(() -> locales.changeStatus(
                cs.id(), new ChangeLocaleStatus(false, current.version())))
                .isInstanceOf(SystemV1Exception.Conflict.class);

        var english = locales.list().stream().filter(locale -> locale.localeCode().equals("en-US"))
                .findFirst().orElseThrow();
        locales.changeStatus(english.id(), new ChangeLocaleStatus(false, english.version()));
        assertThat(locales.resolve("en-US").effectiveLocale()).isEqualTo("cs-CZ");
    }

    /** Translation 必须保持 placeholder 合同，Runtime 支持多 namespace 与逐 Key fallback。 */
    @Test
    void i18nMustValidatePlaceholderAndAggregateRuntimeBundles() {
        var greeting = i18n.createMessage(new CreateMessage(
                "system", "test.greeting", "测试问候", true));
        i18n.saveTranslation(greeting.id(), "zh-CN", new SaveTranslation("你好 {0}", 0L));
        assertThatThrownBy(() -> i18n.saveTranslation(
                greeting.id(), "en-US", new SaveTranslation("Hello", 0L)))
                .isInstanceOf(SystemV1Exception.Conflict.class);
        i18n.saveTranslation(greeting.id(), "en-US", new SaveTranslation("Hello {0}", 0L));

        var material = i18n.createMessage(new CreateMessage(
                "system.material", "missing", "测试缺失", true));
        i18n.saveTranslation(material.id(), "zh-CN", new SaveTranslation("物料不存在", 0L));
        var cs = locales.create(new CreateLocale("cs-CZ", "捷克语", "Čeština", true, 30));
        assertThat(cs.enabled()).isTrue();

        var bundle = runtime.load("cs-CZ", List.of("system", "system.material"));
        assertThat(bundle.effectiveLocale()).isEqualTo("cs-CZ");
        assertThat(bundle.bundles().get("system")).containsEntry("test.greeting", "你好 {0}");
        assertThat(bundle.bundles().get("system.material")).containsEntry("missing", "物料不存在");

        var updated = i18n.updateMessage(material.id(),
                new UpdateMessage(material.description(), false, material.version()));
        assertThat(updated.enabled()).isFalse();
        assertThat(runtime.load("cs-CZ", List.of("system.material")).bundles().get("system.material"))
                .doesNotContainKey("missing");
    }

    /** 测试环境提供稳定认证 Actor；生产继续由 SecurityCurrentActorProvider 解析可信 Token。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestActorConfiguration {
        /** @return 固定测试 Actor，避免审计字段静默回退 */
        @Bean
        CurrentActorProvider testCurrentActorProvider() {
            return () -> Optional.of(new AuditActor("system-v1-test", ActorType.USER));
        }
    }
}
