package io.github.chrisshi.mom.auth;

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


import static org.assertj.core.api.Assertions.*;

/** 使用 Auth 真实 Flyway 表及 Redis 验证 JDBC 复用、事务、I18n HTTP 与既有认证行为。 */
@Testcontainers
@SpringBootTest(classes = AuthApplication.class, properties = {
        "spring.main.banner-mode=off", "spring.cloud.nacos.discovery.enabled=false",
        "mom.security.resource-server.enabled=true", "management.health.redis.enabled=false"})
class AuthI18nStorePostgresqlIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.7-alpine")
            .withCommand("postgres", "-c", "timezone=Asia/Tokyo");
    @Container
    static final org.testcontainers.containers.GenericContainer<?> REDIS =
            new org.testcontainers.containers.GenericContainer<>("redis:8.4.4-alpine").withExposedPorts(6379);
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=mom_auth");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.schemas", () -> "mom_auth");
        registry.add("spring.flyway.default-schema", () -> "mom_auth");
    }
    @Autowired I18nStore store;
    @Autowired I18nManagementService management;
    @Autowired I18nRuntimeService runtime;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationContext context;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM auth_i18n_translation WHERE message_id IN (SELECT id FROM auth_i18n_message_definition WHERE message_key LIKE 'jdbc.test.%')");
        jdbc.update("DELETE FROM auth_i18n_message_definition WHERE message_key LIKE 'jdbc.test.%'");
        actor("jdbc-test-actor");
    }
    @AfterEach
    void clearActor() { SecurityContextHolder.clearContext(); }

    @Test
    void crudAuditCasAndLogicalDeletion() {
        assertThat(context.getBeansOfType(I18nStore.class)).hasSize(1);
        assertThat(store.getClass().getName()).isEqualTo("io.github.chrisshi.mom.i18n.store.JdbcI18nStore");
        Instant before = Instant.now().minusSeconds(1);
        var message = store.createMessage("jdbc.test.audit", "说明", true);
        assertThat(message.id()).matches("[0-9]{1,19}");
        assertThat(message.version()).isZero();
        assertThat(message.updatedAt()).isBetween(before, Instant.now());
        var row = jdbc.queryForMap("SELECT * FROM auth_i18n_message_definition WHERE id=?", message.id());
        assertThat(row.get("created_by")).isEqualTo("jdbc-test-actor");
        assertThat(row.get("updated_by")).isEqualTo("jdbc-test-actor");
        assertThat(row.get("created_at")).isEqualTo(row.get("updated_at"));
        assertThat(store.message(message.id(), false)).isEqualTo(store.message(message.messageKey()));
        actor("jdbc-update-actor");
        assertThat(store.updateMessage(message.id(), null, false, 0)).isTrue();
        assertThat(store.updateMessage(message.id(), "stale", true, 0)).isFalse();
        assertThat(store.message(message.id(), false).description()).isNull();
        assertThat(store.message(message.id(), false).version()).isEqualTo(1);
        var updated = jdbc.queryForMap("SELECT * FROM auth_i18n_message_definition WHERE id=?", message.id());
        assertThat(updated.get("created_at")).isEqualTo(row.get("created_at"));
        assertThat(updated.get("created_by")).isEqualTo("jdbc-test-actor");
        assertThat(updated.get("updated_by")).isEqualTo("jdbc-update-actor");
        assertThat(store.enabledMessages()).noneMatch(m -> m.id().equals(message.id()));
        assertThat(store.messages()).anyMatch(m -> m.id().equals(message.id()));
        var translation = store.createTranslation(message.id(), "zh-CN", "原文");
        assertThat(translation.id()).matches("[0-9]{1,19}");
        assertThat(store.updateTranslation(translation.id(), "更新", 0)).isTrue();
        assertThat(store.updateTranslation(translation.id(), "过期", 0)).isFalse();
        var audit = jdbc.queryForMap("SELECT * FROM auth_i18n_translation WHERE id=?", translation.id());
        assertThat(audit.get("created_by")).isEqualTo("jdbc-update-actor");
        assertThat(audit.get("updated_by")).isEqualTo("jdbc-update-actor");
        assertThat(store.translations(message.id())).singleElement().satisfies(t -> {
            assertThat(t.messageText()).isEqualTo("更新");
            assertThat(t.version()).isEqualTo(1);
        });
        jdbc.update("UPDATE auth_i18n_translation SET deleted=true WHERE id=?", translation.id());
        assertThat(store.translations(message.id())).isEmpty();
        assertThat(store.translations(List.of(message.id()), List.of("zh-CN"))).isEmpty();
        assertThat(store.updateTranslation(translation.id(), "已删除", 1)).isFalse();
        jdbc.update("UPDATE auth_i18n_message_definition SET deleted=true WHERE id=?", message.id());
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
        assertThatThrownBy(() -> management.saveTranslation(first.id(), "fr-FR", "Text {1}", null))
                .isInstanceOf(I18nFailure.class).hasMessage("i18n.placeholder_mismatch");
        assertThat(runtime.load("en-US").messages()).containsEntry("jdbc.test.first", "Text {0}")
                .containsEntry("jdbc.test.second", "文本 {0}")
                .containsEntry("jdbc.test.missing", "jdbc.test.missing")
                .doesNotContainKey(disabled.messageKey());
        assertThat(runtime.load("invalid!").messages()).containsEntry("jdbc.test.first", "文本 {0}");
        var resolver = context.getBean(io.github.chrisshi.mom.core.i18n.I18nMessageResolver.class);
        assertThat(resolver.resolve("jdbc.test.first", java.util.Locale.US, "Alice")).isEqualTo("Text Alice");
        assertThat(resolver.resolve("jdbc.test.second", java.util.Locale.US, "Alice")).isEqualTo("文本 Alice");
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
        SecurityContextHolder.clearContext();
        var mvc = MockMvcBuilders.webAppContextSetup(
                (WebApplicationContext) context)
                .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class)).build();
        var tokens = context.getBean(io.github.chrisshi.mom.security.token.MomTokenStore.class);
        tokens.store("jdbc-http-token", new io.github.chrisshi.mom.security.token.MomTokenPrincipal(
                "jdbc-http-actor", List.of("system:i18n:write"), Instant.now().plusSeconds(300)));
        tokens.store("jdbc-read-token", new io.github.chrisshi.mom.security.token.MomTokenPrincipal(
                "jdbc-http-actor", List.of("auth:i18n:read"), Instant.now().plusSeconds(300)));
        tokens.store("jdbc-no-permission", new io.github.chrisshi.mom.security.token.MomTokenPrincipal(
                "jdbc-http-actor", List.of(), Instant.now().plusSeconds(300)));
        tokens.store("jdbc-legacy-write", new io.github.chrisshi.mom.security.token.MomTokenPrincipal(
                "jdbc-http-actor", List.of("auth:i18n:write"), Instant.now().plusSeconds(300)));
        mvc.perform(get("/i18n/messages")).andExpect(status().isOk());
        mvc.perform(get("/i18n/messages").header("Authorization", "Bearer jdbc-no-permission"))
                .andExpect(status().isOk());
        mvc.perform(post("/i18n/messages").contentType("application/json")
                        .content("{\"messageKey\":\"jdbc.test.anon-denied\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/i18n/messages").header("Authorization", "Bearer jdbc-read-token")
                        .contentType("application/json").content("{\"messageKey\":\"jdbc.test.denied\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/i18n/messages").header("Authorization", "Bearer jdbc-no-permission")
                        .contentType("application/json").content("{\"messageKey\":\"jdbc.test.no-permission\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/i18n/messages").header("Authorization", "Bearer jdbc-legacy-write")
                        .contentType("application/json").content("{\"messageKey\":\"jdbc.test.legacy-denied\"}"))
                .andExpect(status().isForbidden());
        var events = mvc.perform(get("/i18n/runtime/events"))
                .andExpect(request().asyncStarted())
                .andReturn();
        try {
            assertThat(new String(events.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)).contains("I18N_CONNECTED");
            var created = mvc.perform(post("/i18n/messages")
                            .header("Authorization", "Bearer jdbc-http-token").contentType("application/json")
                            .content("{\"messageKey\":\"jdbc.test.http\",\"enabled\":true}"))
                    .andExpect(status().isCreated()).andReturn();
            String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
            assertThat(new String(events.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)).contains("I18N_BUNDLE_CHANGED");
            mvc.perform(put("/i18n/messages/{id}/translations/zh-CN", id)
                            .header("Authorization", "Bearer jdbc-http-token").contentType("application/json").content("{\"messageText\":\"HTTP {0}\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/i18n/messages/{id}/translations", id).header("Authorization", "Bearer jdbc-http-token"))
                    .andExpect(jsonPath("$.data[0].messageText").value("HTTP {0}"));
            mvc.perform(get("/i18n/runtime/bundles").param("locale", "fr-FR"))
                    .andExpect(jsonPath("$.data.messages['jdbc.test.http']").value("HTTP {0}"));
            String committedEvents = new String(events.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
            mvc.perform(put("/i18n/messages/{id}/translations/en-US", id)
                            .header("Authorization", "Bearer jdbc-http-token").contentType("application/json").content("{\"messageText\":\"HTTP {1}\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(put("/i18n/messages/{id}", id)
                            .header("Authorization", "Bearer jdbc-http-token").contentType("application/json").content("{\"description\":null,\"enabled\":false,\"version\":99}"))
                    .andExpect(status().isConflict());
            mvc.perform(post("/i18n/messages")
                            .header("Authorization", "Bearer jdbc-http-token").contentType("application/json").content("{\"messageKey\":\"jdbc.test.http\"}"))
                    .andExpect(status().isConflict());
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                actor("jdbc-rollback");
                management.createMessage("jdbc.test.http.rollback", null, true);
                assertThat(new String(events.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo(committedEvents);
                status.setRollbackOnly();
            });
            assertThat(store.message("jdbc.test.http.rollback")).isNull();
            assertThat(new String(events.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo(committedEvents).doesNotContain("HTTP {0}");
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                management.createMessage("jdbc.test.http.commit", null, true);
                assertThat(new String(events.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                        .isEqualTo(committedEvents);
            });
            assertThat(store.message("jdbc.test.http.commit")).isNotNull();
            assertThat(new String(events.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                    .startsWith(committedEvents).isNotEqualTo(committedEvents);
            assertThat(jdbc.queryForObject("SELECT created_by FROM auth_i18n_message_definition WHERE id=?", String.class, id))
                    .isEqualTo("jdbc-http-actor");
        } finally {
            events.getRequest().getAsyncContext().complete();
        }
    }

    @Test
    void jdbcAndMybatisWritesRollbackTogether() {
        assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(1);
        assertThat(context.getBeansOfType(javax.sql.DataSource.class)).hasSize(1);
        assertThat(context.getBean(org.apache.ibatis.session.SqlSessionFactory.class)
                .getConfiguration().getEnvironment().getDataSource()).isSameAs(jdbc.getDataSource());
        var users = context.getBean(io.github.chrisshi.mom.auth.infrastructure.mapper.UserMapper.class);
        var user = new io.github.chrisshi.mom.auth.infrastructure.entity.UserEntity();
        user.setUsername("jdbc.mixed.rollback");
        user.setDisplayName("混合事务测试");
        user.setPasswordHash("{bcrypt}test-only");
        user.setEnabled(true);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            users.insert(user);
            store.createMessage("jdbc.test.mixed", null, true);
            assertThat(users.selectById(user.getId())).isNotNull();
            assertThat(store.message("jdbc.test.mixed")).isNotNull();
            status.setRollbackOnly();
        });
        assertThat(users.selectById(user.getId())).isNull();
        assertThat(store.message("jdbc.test.mixed")).isNull();
    }

    @Test
    void explicitSchemaFixedClockAndDatabaseErrorClassification() {
        var now = Instant.parse("2026-10-16T03:00:00.123456Z");
        var qualified = new io.github.chrisshi.mom.i18n.store.JdbcI18nStore(
                context.getBean(javax.sql.DataSource.class), "mom_auth.auth_i18n_message_definition",
                "mom_auth.auth_i18n_translation", () -> "1234567890123456789",
                context.getBean(io.github.chrisshi.mom.core.security.CurrentActorProvider.class),
                java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
        var message = qualified.createMessage("jdbc.test.fixed", null, true);
        assertThat(message.id()).isEqualTo("1234567890123456789");
        assertThat(qualified.message(message.id(), false).updatedAt()).isEqualTo(now);
        var missing = new io.github.chrisshi.mom.i18n.store.JdbcI18nStore(
                context.getBean(javax.sql.DataSource.class), "missing_message_table", "missing_translation_table",
                () -> "1", java.util.Optional::empty, java.time.Clock.systemUTC());
        assertThatThrownBy(missing::messages).isInstanceOf(org.springframework.jdbc.BadSqlGrammarException.class);
    }

    @Test
    void realLoginTokenExpiryPermissionsAndLogoutRemainUnchanged() throws Exception {
        var users = context.getBean(io.github.chrisshi.mom.auth.application.UserApplication.class);
        var user = users.create("jdbc.security", "JdbcPassword@123", "安全回归", true);
        SecurityContextHolder.clearContext();
        var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class)).build();
        mvc.perform(post("/sessions/login").contentType("application/json")
                        .content("{\"username\":\"jdbc.security\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        var response = mvc.perform(post("/sessions/login").contentType("application/json")
                        .content("{\"username\":\"jdbc.security\",\"password\":\"JdbcPassword@123\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(), "$.data.accessToken");
        var tokens = context.getBean(io.github.chrisshi.mom.security.token.MomTokenStore.class);
        assertThat(tokens.find(token)).get().satisfies(principal -> assertThat(principal.userId()).isEqualTo(user.id()));
        mvc.perform(get("/i18n/messages").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/i18n/messages").header("Authorization", "Bearer unknown-jdbc-token"))
                .andExpect(status().isUnauthorized());
        // 仅在测试内用过去时钟写入尚有 Redis TTL、但业务有效期已过的快照，验证实际内省器拒绝它。
        var past = java.time.Clock.fixed(Instant.now().minusSeconds(7200), java.time.ZoneOffset.UTC);
        new io.github.chrisshi.mom.security.token.RedisMomTokenStore(
                context.getBean(org.springframework.data.redis.core.StringRedisTemplate.class),
                context.getBean(tools.jackson.databind.json.JsonMapper.class), past)
                .store("jdbc-expired-token", new io.github.chrisshi.mom.security.token.MomTokenPrincipal(
                        user.id(), List.of("auth:i18n:read"), Instant.now().minusSeconds(3600)));
        mvc.perform(get("/i18n/messages").header("Authorization", "Bearer jdbc-expired-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/sessions/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(tokens.find(token)).isEmpty();
        mvc.perform(post("/sessions/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        actor("jdbc-security-cleanup");
        users.update(user.id(), "安全回归", false, user.version());
        SecurityContextHolder.clearContext();
        mvc.perform(post("/sessions/login").contentType("application/json")
                        .content("{\"username\":\"jdbc.security\",\"password\":\"JdbcPassword@123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.account_disabled"));
    }

    private static void actor(String id) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id, "unused", List.of()));
    }
}
