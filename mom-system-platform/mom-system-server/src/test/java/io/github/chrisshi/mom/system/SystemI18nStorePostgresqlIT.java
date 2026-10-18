package io.github.chrisshi.mom.system;

import io.github.chrisshi.mom.i18n.management.I18nFailure;
import io.github.chrisshi.mom.i18n.management.I18nManagementService;
import io.github.chrisshi.mom.i18n.runtime.I18nRuntimeService;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;

/** 使用真实 System Flyway 表验证旧 Store 与 JDBC Store 共同遵守的持久化契约。 */
@Testcontainers
@SpringBootTest(classes = MomSystemApplication.class, properties = {
        "spring.config.additional-location=file:src/main/resources/application.yml",
        "spring.main.banner-mode=off", "spring.cloud.nacos.discovery.enabled=false",
        "mom.security.resource-server.enabled=true", "management.health.redis.enabled=false"})
class SystemI18nStorePostgresqlIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.7-alpine")
            .withCommand("postgres", "-c", "timezone=Asia/Tokyo");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=mom_system");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.schemas", () -> "mom_system");
        registry.add("spring.flyway.default-schema", () -> "mom_system");
    }
    @Autowired I18nStore store;
    @Autowired I18nManagementService management;
    @Autowired I18nRuntimeService runtime;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationContext context;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM system_i18n_translation WHERE message_id IN (SELECT id FROM system_i18n_message_definition WHERE message_key LIKE 'jdbc.test.%')");
        jdbc.update("DELETE FROM system_i18n_message_definition WHERE message_key LIKE 'jdbc.test.%'");
        jdbc.update("DELETE FROM system_dictionary WHERE dictionary_code='jdbc.test.mixed'");
        jdbc.update("DELETE FROM system_supported_locale WHERE locale_code='cs-CZ'");
        jdbc.update("UPDATE system_supported_locale SET is_default=false WHERE is_default=true");
        jdbc.update("UPDATE system_supported_locale SET enabled=true, is_default=(locale_code='zh-CN')");
        actor("jdbc-test-actor");
    }
    @AfterEach
    void clearActor() { SecurityContextHolder.clearContext(); }

    @Test
    void codeRuleMessagesUpgradeFromV24PreservesExistingAndCustomTranslations() {
        verifyCodeRuleMessagesUpgrade("24");
    }

    @Test
    void readableCodeRuleMessagesUpgradeFromV25PreservesExistingAndCustomTranslations() {
        verifyCodeRuleMessagesUpgrade("25");
    }

    @Test
    void usageMetadataMessagesUpgradeFromV26PreservesExistingAndCustomTranslations() {
        verifyCodeRuleMessagesUpgrade("26");
    }

    private void verifyCodeRuleMessagesUpgrade(String fromVersion) {
        String schema = "code_rule_messages_upgrade";
        String originalDefinitions = fromVersion.equals("24") ? "message_key NOT LIKE 'system.coderules.%'"
                : fromVersion.equals("25") ? "id NOT LIKE '9672%' AND id NOT LIKE '9673%'" : "id NOT LIKE '9673%'";
        String originalTranslations = fromVersion.equals("24") ? "d.message_key NOT LIKE 'system.coderules.%'"
                : fromVersion.equals("25") ? "t.id NOT LIKE '9672%' AND t.id NOT LIKE '9673%'" : "t.id NOT LIKE '9673%'";
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + "&currentSchema=" + schema, POSTGRES.getUsername(), POSTGRES.getPassword());
        var isolated = new JdbcTemplate(source);
        try {
            org.flywaydb.core.Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration/system").target(fromVersion).load().migrate();
            isolated.update("""
                    INSERT INTO system_i18n_message_definition
                    (id,message_key,description,enabled,version,deleted,created_by,created_at,updated_by,updated_at)
                    VALUES ('9991000000000000001','system.custom.label','自定义文案',true,7,false,'admin',CURRENT_TIMESTAMP,'admin',CURRENT_TIMESTAMP)
                    """);
            isolated.update("""
                    INSERT INTO system_i18n_translation
                    (id,message_id,locale_code,message_text,version,deleted,created_by,created_at,updated_by,updated_at)
                    VALUES ('9991000000000000002','9991000000000000001','zh-CN','保留自定义译文',9,false,'admin',CURRENT_TIMESTAMP,'admin',CURRENT_TIMESTAMP)
                    """);
            var definitions = isolated.queryForList("SELECT * FROM system_i18n_message_definition ORDER BY id");
            var translations = isolated.queryForList("SELECT * FROM system_i18n_translation ORDER BY id");
            org.flywaydb.core.Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration/system").load().migrate();
            assertThat(isolated.queryForList("SELECT * FROM system_i18n_message_definition WHERE " + originalDefinitions + " ORDER BY id"))
                    .isEqualTo(definitions);
            assertThat(isolated.queryForList("SELECT t.* FROM system_i18n_translation t JOIN system_i18n_message_definition d ON t.message_id=d.id WHERE " + originalTranslations + " ORDER BY t.id"))
                    .isEqualTo(translations);
            assertThat(isolated.queryForObject("SELECT COUNT(*) FROM system_i18n_message_definition WHERE message_key LIKE 'system.coderules.%'", Long.class)).isEqualTo(41);
        } finally { jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }

    @Test
    void codeRuleManagementMessagesAreLoadedFromSystemDatabaseInBothLocales() {
        var chinese = runtime.load("zh-CN").messages();
        var english = runtime.load("en-US").messages();
        assertThat(chinese).containsEntry("system.coderules.title", "编码规则")
                .containsEntry("system.coderules.search", "查询规则")
                .containsEntry("system.coderules.composition", "编码组成")
                .containsEntry("system.coderules.usagescenario", "使用场景");
        assertThat(english).containsEntry("system.coderules.title", "Code Rules")
                .containsEntry("system.coderules.search", "Search Rules")
                .containsEntry("system.coderules.composition", "Code Composition")
                .containsEntry("system.coderules.usagescenario", "Usage Scenario");
        var keys = jdbc.queryForList("SELECT message_key FROM system_i18n_message_definition WHERE message_key LIKE 'system.coderules.%'", String.class);
        assertThat(keys).hasSize(41);
        assertThat(chinese.keySet()).containsAll(keys);
        assertThat(english.keySet()).containsAll(keys);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_i18n_translation t JOIN system_i18n_message_definition d ON t.message_id=d.id WHERE d.message_key LIKE 'system.coderules.%'", Long.class)).isEqualTo(82);
    }

    @Test
    void hostMustExposeOneJdbcStoreAndOneLocalTransaction() {
        assertThat(context.getBeansOfType(I18nStore.class)).hasSize(1);
        assertThat(store.getClass().getName()).isEqualTo("io.github.chrisshi.mom.i18n.store.JdbcI18nStore");
        assertThat(context.getBeansOfType(javax.sql.DataSource.class)).hasSize(1);
        assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(1);
        var source = context.getBean(javax.sql.DataSource.class);
        assertThat(((org.springframework.jdbc.support.JdbcTransactionManager) transactions).getDataSource())
                .isSameAs(source);
        assertThat(context.getBean(org.apache.ibatis.session.SqlSessionFactory.class)
                .getConfiguration().getEnvironment().getDataSource()).isSameAs(source);
    }

    @Test
    void crudAuditCasAndLogicalDeletion() {
        assertThat(context.getBeansOfType(I18nStore.class)).hasSize(1);
        Instant before = Instant.now().minusSeconds(1);
        var message = store.createMessage("jdbc.test.audit", "说明", true);
        assertThat(message.id()).matches("[0-9]{1,19}");
        assertThat(message.version()).isZero();
        assertThat(message.updatedAt()).isBetween(before, Instant.now());
        var row = jdbc.queryForMap("SELECT * FROM system_i18n_message_definition WHERE id=?", message.id());
        assertThat(row.get("created_by")).isEqualTo("jdbc-test-actor");
        assertThat(row.get("updated_by")).isEqualTo("jdbc-test-actor");
        assertThat(row.get("created_at")).isEqualTo(row.get("updated_at"));
        assertThat(store.message(message.id(), false)).isEqualTo(store.message(message.messageKey()));
        actor("jdbc-update-actor");
        assertThat(store.updateMessage(message.id(), null, false, 0)).isTrue();
        assertThat(store.updateMessage(message.id(), "stale", true, 0)).isFalse();
        assertThat(store.message(message.id(), false).description()).isNull();
        assertThat(store.message(message.id(), false).version()).isEqualTo(1);
        var updated = jdbc.queryForMap("SELECT * FROM system_i18n_message_definition WHERE id=?", message.id());
        assertThat(updated.get("created_at")).isEqualTo(row.get("created_at"));
        assertThat(updated.get("created_by")).isEqualTo("jdbc-test-actor");
        assertThat(updated.get("updated_by")).isEqualTo("jdbc-update-actor");
        assertThat(store.enabledMessages()).noneMatch(m -> m.id().equals(message.id()));
        assertThat(store.messages()).anyMatch(m -> m.id().equals(message.id()));
        var translation = store.createTranslation(message.id(), "zh-CN", "原文");
        assertThat(translation.id()).matches("[0-9]{1,19}");
        assertThat(store.updateTranslation(translation.id(), "更新", 0)).isTrue();
        assertThat(store.updateTranslation(translation.id(), "过期", 0)).isFalse();
        var audit = jdbc.queryForMap("SELECT * FROM system_i18n_translation WHERE id=?", translation.id());
        assertThat(audit.get("created_by")).isEqualTo("jdbc-update-actor");
        assertThat(audit.get("updated_by")).isEqualTo("jdbc-update-actor");
        assertThat(store.translations(message.id())).singleElement().satisfies(t -> {
            assertThat(t.messageText()).isEqualTo("更新");
            assertThat(t.version()).isEqualTo(1);
        });
        jdbc.update("UPDATE system_i18n_translation SET deleted=true WHERE id=?", translation.id());
        assertThat(store.translations(message.id())).isEmpty();
        assertThat(store.translations(List.of(message.id()), List.of("zh-CN"))).isEmpty();
        assertThat(store.updateTranslation(translation.id(), "已删除", 1)).isFalse();
        jdbc.update("UPDATE system_i18n_message_definition SET deleted=true WHERE id=?", message.id());
        assertThat(store.message(message.id(), false)).isNull();
        assertThat(store.message(message.messageKey())).isNull();
        assertThat(store.messages()).noneMatch(m -> m.id().equals(message.id()));
        assertThat(store.updateMessage(message.id(), null, true, 1)).isFalse();
    }

    @Test
    void bulkRuntimeFallbackDuplicatesAndPlaceholders() {
        var first = management.createMessage("jdbc.test.first", null, true);
        var second = management.createMessage("jdbc.test.second", null, true);
        management.createMessage("jdbc.test.missing", null, true);
        var disabled = management.createMessage("jdbc.test.disabled", null, false);
        management.saveTranslation(first.id(), "zh-CN", "文本 {0}", null);
        management.saveTranslation(second.id(), "zh-CN", "文本 {0}", null);
        management.saveTranslation(first.id(), "en-US", "Text {0}", null);
        assertThat(management.duplicateMessageKeys(first.id(), "zh-CN", "文本 {0}"))
                .containsExactly("jdbc.test.second");
        assertThatThrownBy(() -> management.saveTranslation(first.id(), "en-US", "Text {1}", 0L))
                .isInstanceOf(I18nFailure.class).hasMessage("i18n.placeholder_mismatch");
        assertThat(runtime.load("en-US").messages()).containsEntry("jdbc.test.first", "Text {0}")
                .containsEntry("jdbc.test.second", "文本 {0}")
                .containsEntry("jdbc.test.missing", "jdbc.test.missing")
                .doesNotContainKey(disabled.messageKey());
        assertThat(runtime.load("fr-FR").messages()).containsEntry("jdbc.test.first", "文本 {0}");
        assertThat(store.translations(List.of(first.id(), second.id()), List.of("zh-CN", "en-US"))).hasSize(3);
        assertThat(store.translations(List.of(), List.of("zh-CN"))).isEmpty();
        assertThat(store.translations(List.of(first.id()), List.of())).isEmpty();
    }

    @Test
    void databaseExceptionsAreNotReportedAsSuccess() {
        var m = store.createMessage("jdbc.test.unique", null, true);
        assertThatThrownBy(() -> store.createMessage(m.messageKey(), null, true)).isInstanceOf(DuplicateKeyException.class);
        store.createTranslation(m.id(), "zh-CN", "文本");
        assertThatThrownBy(() -> store.createTranslation(m.id(), "zh-CN", "重复")).isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> store.createTranslation(m.id(), "en-US", "<b>bad</b>"))
                .isInstanceOf(DataIntegrityViolationException.class).isNotInstanceOf(DuplicateKeyException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> store.createMessage("jdbc.test.noactor", null, true))
                .isInstanceOf(DataIntegrityViolationException.class).isNotInstanceOf(DuplicateKeyException.class);
        assertThat(store.message("jdbc.test.noactor")).isNull();
    }

    @Test
    void concurrentCasHasExactlyOneWinner() throws Exception {
        var m = store.createMessage("jdbc.test.cas", null, true);
        var t = store.createTranslation(m.id(), "zh-CN", "并发前");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<List<Boolean>> update = () -> {
                actor("jdbc-cas");
                try { assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return List.of(store.updateMessage(m.id(), "并发", true, 0),
                            store.updateTranslation(t.id(), "并发后", 0));
                } finally { SecurityContextHolder.clearContext(); }
            };
            var one = executor.submit(update);
            var two = executor.submit(update);
            start.countDown();
            var first = one.get(10, TimeUnit.SECONDS);
            var second = two.get(10, TimeUnit.SECONDS);
            assertThat(List.of(first.get(0), second.get(0))).containsExactlyInAnyOrder(true, false);
            assertThat(List.of(first.get(1), second.get(1))).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void rowLockAndRollbackUseHostTransaction() throws Exception {
        var m = store.createMessage("jdbc.test.lock", null, true);
        var tx = new TransactionTemplate(transactions);
        try (var executor = Executors.newSingleThreadExecutor()) {
            tx.executeWithoutResult(status -> {
                store.message(m.id(), true);
                var blocked = executor.submit(() -> {
                    actor("jdbc-lock");
                    try {
                        new TransactionTemplate(transactions).executeWithoutResult(other -> {
                            jdbc.execute("SET LOCAL lock_timeout='250ms'");
                            store.message(m.id(), true);
                        });
                    } finally { SecurityContextHolder.clearContext(); }
                });
                assertThatThrownBy(() -> blocked.get(5, TimeUnit.SECONDS))
                        .rootCause().isInstanceOfSatisfying(SQLException.class,
                                error -> assertThat(error.getSQLState()).isEqualTo("55P03"));
                store.createTranslation(m.id(), "zh-CN", "回滚");
                store.updateMessage(m.id(), "回滚", false, 0);
                status.setRollbackOnly();
            });
        }
        assertThat(store.translations(m.id())).isEmpty();
        assertThat(store.message(m.id(), false).version()).isZero();
        assertThat(store.message(m.id(), false).enabled()).isTrue();
        tx.executeWithoutResult(status -> assertThat(store.updateMessage(m.id(), "提交", true, 0)).isTrue());
    }

    @Test
    void managementApiAndSseFollowDatabaseCommitAndRollback() throws Exception {
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        var mvc = MockMvcBuilders.webAppContextSetup(
                (WebApplicationContext) context)
                .apply(springSecurity()).build();
        var identity = opaqueToken()
                .attributes(a -> a.put("sub", "jdbc-http-actor"))
                .authorities(new SimpleGrantedAuthority("system:i18n:write"));
        mvc.perform(get("/i18n/messages").with(anonymous()))
                .andExpect(status().isOk());
        var events = mvc.perform(get("/i18n/runtime/events").with(anonymous()))
                .andExpect(request().asyncStarted())
                .andReturn();
        try {
            assertThat(new String(events.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)).contains("I18N_CONNECTED");
            var created = mvc.perform(post("/i18n/messages")
                            .with(identity).contentType("application/json")
                            .content("{\"messageKey\":\"jdbc.test.http\",\"enabled\":true}"))
                    .andExpect(status().isCreated()).andReturn();
            String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
            assertThat(new String(events.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)).contains("I18N_BUNDLE_CHANGED");
            mvc.perform(put("/i18n/messages/{id}/translations/zh-CN", id)
                            .with(identity).contentType("application/json").content("{\"messageText\":\"HTTP {0}\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/i18n/messages/{id}/translations", id).with(identity))
                    .andExpect(jsonPath("$.data[0].messageText").value("HTTP {0}"));
            mvc.perform(get("/i18n/runtime/bundles").param("locale", "fr-FR"))
                    .andExpect(jsonPath("$.data.messages['jdbc.test.http']").value("HTTP {0}"));
            String committedEvents = new String(events.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
            mvc.perform(put("/i18n/messages/{id}/translations/en-US", id)
                            .with(identity).contentType("application/json").content("{\"messageText\":\"HTTP {1}\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(put("/i18n/messages/{id}", id)
                            .with(identity).contentType("application/json").content("{\"description\":null,\"enabled\":false,\"version\":99}"))
                    .andExpect(status().isConflict());
            mvc.perform(post("/i18n/messages")
                            .with(identity).contentType("application/json").content("{\"messageKey\":\"jdbc.test.http\"}"))
                    .andExpect(status().isConflict());
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                actor("jdbc-rollback");
                management.createMessage("jdbc.test.http.rollback", null, true);
                assertThat(new String(events.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo(committedEvents);
                status.setRollbackOnly();
            });
            assertThat(store.message("jdbc.test.http.rollback")).isNull();
            assertThat(new String(events.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo(committedEvents).doesNotContain("HTTP {0}");
            assertThat(jdbc.queryForObject("SELECT created_by FROM system_i18n_message_definition WHERE id=?", String.class, id))
                    .isEqualTo("jdbc-http-actor");
        } finally {
            events.getRequest().getAsyncContext().complete();
        }
    }

    @Test
    void jdbcAndMybatisWritesRollbackTogether() {
        var mapper = context.getBean(io.github.chrisshi.mom.system.infrastructure.mapper.DictionaryTypeMapper.class);
        var type = new io.github.chrisshi.mom.system.infrastructure.entity.DictionaryTypeEntity();
        type.setCode("jdbc.test.mixed");
        type.setName("混合事务测试");
        type.setEnabled(true);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            mapper.insert(type);
            store.createMessage("jdbc.test.mixed", null, true);
            assertThat(mapper.selectById(type.getId())).isNotNull();
            assertThat(store.message("jdbc.test.mixed")).isNotNull();
            status.setRollbackOnly();
        });
        assertThat(mapper.selectById(type.getId())).isNull();
        assertThat(store.message("jdbc.test.mixed")).isNull();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            mapper.insert(type);
            store.createMessage("jdbc.test.mixed", null, true);
        });
        assertThat(mapper.selectById(type.getId())).isNotNull();
        assertThat(store.message("jdbc.test.mixed")).isNotNull();
    }

    @Test
    void localeEndpointAndDynamicDefaultKeepSystemPolicy() throws Exception {
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .apply(springSecurity()).build();
        // Gateway 的 /system/** 使用 StripPrefix=1，前端 /system/i18n/locales 对应此接口。
        mvc.perform(get("/i18n/locales").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].localeCode").value("zh-CN"))
                .andExpect(jsonPath("$.data[0].defaultLocale").value(true))
                .andExpect(jsonPath("$.data[1].localeCode").value("en-US"));
        mvc.perform(get("/i18n/locale-catalog").with(anonymous()))
                .andExpect(status().isOk());
        mvc.perform(post("/i18n/messages").with(opaqueToken()
                        .authorities(new SimpleGrantedAuthority("system:i18n:read")))
                        .contentType("application/json").content("{\"messageKey\":\"jdbc.test.forbidden\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/i18n/messages").with(anonymous()).contentType("application/json")
                        .content("{\"messageKey\":\"jdbc.test.anonymous-denied\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/i18n/messages").with(opaqueToken()).contentType("application/json")
                        .content("{\"messageKey\":\"jdbc.test.no-permission\"}"))
                .andExpect(status().isForbidden());
        actor("jdbc-locale");
        var locales = context.getBean(io.github.chrisshi.mom.system.application.i18n.SupportedLocaleApplication.class);
        var message = management.createMessage("jdbc.test.locale", null, true);
        management.saveTranslation(message.id(), "zh-CN", "你好 {0}", null);
        management.saveTranslation(message.id(), "en-US", "Hello {0}", null);
        var resolver = context.getBean(io.github.chrisshi.mom.core.i18n.I18nMessageResolver.class);
        assertThat(resolver.resolve(message.messageKey(), java.util.Locale.US, "MOM")).isEqualTo("Hello MOM");
        var english = locales.list().stream().filter(l -> l.localeCode().equals("en-US")).findFirst().orElseThrow();
        locales.makeDefault(english.id(), english.version());
        assertThat(runtime.load("fr-FR").effectiveLocale()).isEqualTo("en-US");
        assertThat(runtime.load("fr-FR").messages()).containsEntry("jdbc.test.locale", "Hello {0}");
        mvc.perform(get("/i18n/locales").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].defaultLocale").value(false))
                .andExpect(jsonPath("$.data[1].defaultLocale").value(true));
        actor("jdbc-locale");
        var chinese = locales.list().stream().filter(l -> l.localeCode().equals("zh-CN")).findFirst().orElseThrow();
        locales.changeStatus(chinese.id(), new io.github.chrisshi.mom.system.application.i18n.I18nModels.ChangeLocaleStatus(false, chinese.version()));
        assertThat(runtime.load("zh-CN").effectiveLocale()).isEqualTo("en-US");
        var cs = locales.create(new io.github.chrisshi.mom.system.application.i18n.I18nModels.CreateLocale(
                "cs-CZ", "捷克语", "Čeština", true, 30));
        assertThat(runtime.load("cs-CZ").effectiveLocale()).isEqualTo("cs-CZ");
        assertThat(runtime.load("cs-CZ").messages()).containsEntry("jdbc.test.locale", "Hello {0}");
        mvc.perform(get("/i18n/locales").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].enabled").value(false))
                .andExpect(jsonPath("$.data[2].localeCode").value(cs.localeCode()));
    }

    private static void actor(String id) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id, "unused", List.of()));
    }
}
