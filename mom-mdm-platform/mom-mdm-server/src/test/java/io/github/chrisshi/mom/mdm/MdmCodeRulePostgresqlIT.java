package io.github.chrisshi.mom.mdm;

import io.github.chrisshi.mom.coderule.CodeRuleDefinition;
import io.github.chrisshi.mom.coderule.CodeRuleFailure;
import io.github.chrisshi.mom.coderule.CodeRulePageParams;
import io.github.chrisshi.mom.coderule.CodeRuleView;
import io.github.chrisshi.mom.coderule.field.CodeFieldMetadata;
import io.github.chrisshi.mom.coderule.field.CodeFieldRegistry;
import io.github.chrisshi.mom.coderule.field.CodeGenerationContext;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentException;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentMetadata;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRenderContext;
import io.github.chrisshi.mom.coderule.application.CodeRuleApplication;
import io.github.chrisshi.mom.coderule.store.CodeRuleStore;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.security.token.MomTokenPrincipal;
import io.github.chrisshi.mom.security.token.MomTokenStore;
import io.github.chrisshi.mom.security.token.MomTokenFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import io.github.chrisshi.mom.webmvc.coderule.CodeRuleController;
import io.github.chrisshi.mom.webmvc.autoconfigure.CodeRuleWebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import static org.mockito.Mockito.mock;
import io.github.chrisshi.mom.webmvc.coderule.CodeRuleManagementAccess;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.coderule.CodeRulePageParams;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.Clock;
import tools.jackson.databind.json.JsonMapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** 真实 MDM Flyway/PostgreSQL 与 Redis Opaque Token 的规则闭环测试。 */
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.boot.test.context.SpringBootTest(classes = MomMdmApplication.class, properties = {
        "spring.main.banner-mode=off", "spring.cloud.nacos.discovery.enabled=false",
        "mom.security.resource-server.enabled=true", "management.health.redis.enabled=false"})
class MdmCodeRulePostgresqlIT {
    @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.7-alpine");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=mom_mdm");
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.flyway.schemas", () -> "mom_mdm");
        registry.add("spring.flyway.default-schema", () -> "mom_mdm");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
    @Autowired CodeRuleApplication rules;
    @MockitoSpyBean CodeRuleStore store;
    @Autowired CodeSegmentRegistry segments;
    @Autowired CodeFieldRegistry fields;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean MomTokenStore tokens;
    @Autowired StringRedisTemplate redis;
    @Autowired JsonMapper json;
    @Autowired ApplicationContext context;
    @Autowired WebApplicationContext web;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = webAppContextSetup(web).apply(springSecurity()).build();
        jdbc.update("DELETE FROM mdm_code_rule_revision WHERE rule_id IN (SELECT id FROM mdm_code_rule WHERE rule_code LIKE 'it.code.%')");
        jdbc.update("DELETE FROM mdm_code_rule WHERE rule_code LIKE 'it.code.%'");
        actor();
    }
    private static void actor() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("code-rule-test", "", List.of()));
    }
    private static CodeRuleDefinition definition() {
        return new CodeRuleDefinition(1, List.of(
                new CodeRuleDefinition.Segment("prefix", "LITERAL", Map.of("value", "M-")),
                new CodeRuleDefinition.Segment("field", "BUSINESS_FIELD", Map.of("fieldKey", "mdm.materialCategory.code")),
                new CodeRuleDefinition.Segment("seq", "SEQUENCE", Map.of("width", 4, "step", 1))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity { }

    @Test void controllerUsesConfiguredOwnerPermissionsThroughMethodSecurity() {
        CodeRuleApplication application = mock(CodeRuleApplication.class);
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CodeRuleWebMvcAutoConfiguration.class))
                .withUserConfiguration(MethodSecurity.class)
                .withPropertyValues("mom.code-rule.enabled=true", "mom.code-rule.owner=test_owner",
                        "mom.security.resource-server.enabled=true",
                        "mom.code-rule.management.read-authority=test_owner:rules:view",
                        "mom.code-rule.management.write-authority=test_owner:rules:manage")
                .withBean(CodeRuleApplication.class, () -> application)
                .withBean(CodeSegmentRegistry.class, () -> new CodeSegmentRegistry(List.of()))
                .withBean(I18nMessageResolver.class, () -> mock(I18nMessageResolver.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CodeRuleManagementAccess.class);
                    var controller = context.getBean(CodeRuleController.class);
                    var query = new PageQuery<>(new CodeRulePageParams(null, null), 1, 20);
                    try {
                        for (String authority : List.of("mdm:code-rule:read", "system:code-rule:read")) {
                            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                                    "reader", "", List.of(new SimpleGrantedAuthority(authority))));
                            assertThatThrownBy(() -> controller.search(query)).isInstanceOf(AccessDeniedException.class);
                        }
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                                "reader", "", List.of(new SimpleGrantedAuthority("test_owner:rules:view"))));
                        controller.search(query);
                        verify(application).search(query);
                        assertThatThrownBy(() -> controller.create(new CodeRuleController.CreateRequest("x", "测试场景", null, null)))
                                .isInstanceOf(AccessDeniedException.class);
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                                "writer", "", List.of(new SimpleGrantedAuthority("test_owner:rules:manage"))));
                        controller.create(new CodeRuleController.CreateRequest("x", "测试场景", null, null));
                        verify(application).create("x", "测试场景", null, null);
                    } finally { SecurityContextHolder.clearContext(); }
                });
    }
    @Test void secondOwnerAutoConfigurationUsesItsOwnDataSourceAndTables() {
        jdbc.execute("CREATE SCHEMA code_rule_test_owner");
        jdbc.execute("CREATE TABLE code_rule_test_owner.test_owner_code_rule (LIKE mdm_code_rule INCLUDING ALL)");
        jdbc.execute("CREATE TABLE code_rule_test_owner.test_owner_code_rule_revision (LIKE mdm_code_rule_revision INCLUDING ALL)");
        var secondSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                PG.getJdbcUrl() + "&currentSchema=code_rule_test_owner", PG.getUsername(), PG.getPassword());
        var nextId = new java.util.concurrent.atomic.AtomicLong(8000000000000000000L);
        try {
            var mdm = rules.create("it.code.isolation", "测试场景", null, definition());
            new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                    .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                            io.github.chrisshi.mom.coderule.autoconfigure.CodeRuleAutoConfiguration.class))
                    .withUserConfiguration(SecondOwnerTransactions.class)
                    .withPropertyValues("mom.code-rule.enabled=true", "mom.code-rule.owner=test_owner")
                    .withBean(javax.sql.DataSource.class, () -> secondSource)
                    .withBean(org.springframework.transaction.PlatformTransactionManager.class,
                            () -> new org.springframework.jdbc.datasource.DataSourceTransactionManager(secondSource))
                    .withBean(JsonMapper.class, () -> json)
                    .withBean(io.github.chrisshi.mom.core.security.CurrentActorProvider.class,
                            () -> context.getBean(io.github.chrisshi.mom.core.security.CurrentActorProvider.class))
                    .withBean(Clock.class, Clock::systemUTC)
                    .withBean(io.github.chrisshi.mom.core.id.IdGenerator.class, () -> () -> Long.toString(nextId.incrementAndGet()))
                    .run(second -> {
                        assertThat(second).hasNotFailed();
                        var secondRules = second.getBean(CodeRuleApplication.class);
                        var draft = secondRules.create("it.code.isolation", "测试场景", null, new CodeRuleDefinition(1, List.of()));
                        assertThat(secondRules.detail(draft.id()).definition().segments()).isEmpty();
                        assertThat(second.getBean(CodeSegmentRegistry.class).catalog(1))
                                .extracting(CodeSegmentMetadata::type).doesNotContain("BUSINESS_FIELD");
                        assertThat(second.getBean(CodeFieldRegistry.class).catalog()).isEmpty();
                        assertThatThrownBy(() -> secondRules.detail(mdm.id())).isInstanceOf(CodeRuleFailure.class);
                        assertThatThrownBy(() -> rules.detail(draft.id())).isInstanceOf(CodeRuleFailure.class);
                        assertThat(secondRules.search(new PageQuery<>(new CodeRulePageParams(null, null), 1, 20)).records())
                                .extracting(CodeRuleView::id).containsExactly(draft.id());
                        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mdm_code_rule WHERE id=?", Long.class, draft.ruleId())).isZero();
                    });
        } finally { jdbc.execute("DROP SCHEMA code_rule_test_owner CASCADE"); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @org.springframework.transaction.annotation.EnableTransactionManagement
    static class SecondOwnerTransactions { }

    @Test void discoveredBeansAndJsonbRoundTrip() {
        assertThat(context.getBeansOfType(CodeRuleStore.class)).hasSize(1);
        assertThat(context.getBeansOfType(CodeRuleApplication.class)).hasSize(1);
        assertThat(context.getBeansOfType(CodeSegmentRegistry.class)).hasSize(1);
        assertThat(context.getBeansOfType(CodeFieldRegistry.class)).hasSize(1);
        assertThat(context.getBeansOfType(io.github.chrisshi.mom.webmvc.coderule.CodeRuleController.class)).hasSize(1);
        assertThat(segments.catalog(1)).extracting(CodeSegmentMetadata::type)
                .containsExactlyInAnyOrder("LITERAL", "DATE", "SEQUENCE", "BUSINESS_FIELD");
        assertThat(fields.catalog()).extracting(CodeFieldMetadata::fieldKey).contains("mdm.materialCategory.code");
        var created = rules.create("it.code.json", "测试场景", null, definition());
        assertThat(jdbc.queryForObject("SELECT pg_typeof(definition)::text FROM mdm_code_rule_revision WHERE id=?",
                String.class, created.id())).isEqualTo("jsonb");
        assertThat(rules.detail(created.id()).definition()).isEqualTo(definition());
        assertThat(rules.search(new PageQuery<>(new CodeRulePageParams("it.code.json", "DRAFT"), 1, 10))
                .records()).extracting(CodeRuleView::id).containsExactly(created.id());
    }

    @Test void oversizedPageNumberFailsAsBadRequestBeforeDatabaseAccess() {
        assertThatThrownBy(() -> rules.search(new PageQuery<>(
                new CodeRulePageParams(null, null), Long.MAX_VALUE, 10)))
                .isInstanceOfSatisfying(CodeRuleFailure.class,
                        failure -> assertThat(failure.kind()).isEqualTo(CodeRuleFailure.Kind.BAD_REQUEST));
    }

    @Test void jsonbPreservesNullParameterAndSegmentArrayOrder() {
        Map<String, Object> partial = new LinkedHashMap<>();
        partial.put("value", null);
        var definition = new CodeRuleDefinition(1, List.of(
                new CodeRuleDefinition.Segment("first", "LITERAL", partial),
                new CodeRuleDefinition.Segment("second", "SEQUENCE", Map.of("width", 4))));
        var created = rules.create("it.code.null", "测试场景", null, definition);
        assertThat(rules.detail(created.id()).definition()).isEqualTo(definition);
        assertThat(jdbc.queryForObject("SELECT definition #>> '{segments,0,id}' FROM mdm_code_rule_revision WHERE id=?",
                String.class, created.id())).isEqualTo("first");
        assertThat(jdbc.queryForObject("SELECT definition #>> '{segments,1,id}' FROM mdm_code_rule_revision WHERE id=?",
                String.class, created.id())).isEqualTo("second");
        assertThat(jdbc.queryForObject("SELECT jsonb_exists(definition #> '{segments,0,params}', 'value') FROM mdm_code_rule_revision WHERE id=?",
                Boolean.class, created.id())).isTrue();
    }

    @Test void databaseEnforcesOneDraftAndOneEnabledPerRule() {
        var first = rules.create("it.code.unique", "测试场景", null, definition());
        assertThatThrownBy(() -> store.insertRevision("9000000000000000002", first.ruleId(), 2, "DRAFT", definition()))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        var active = rules.enable(first.id(), first.ruleVersion());
        assertThatThrownBy(() -> store.insertRevision("9000000000000000003", first.ruleId(), 3, "ENABLED", definition()))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(rules.detail(active.id()).status()).isEqualTo("ENABLED");
    }

    @Test void realProviderReadsEnabledMaterialCategory() {
        jdbc.update("DELETE FROM mdm_material_category WHERE code='CODE_RULE_IT'");
        jdbc.update("""
                INSERT INTO mdm_material_category
                (id,code,name,sort,status,created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES ('9000000000000000001','CODE_RULE_IT','测试分类',1,'ENABLED',CURRENT_TIMESTAMP,'test',CURRENT_TIMESTAMP,'test',0,false)
                """);
        var context = new CodeSegmentRenderContext(Clock.systemUTC(),
                new CodeGenerationContext(Map.of("materialCategoryId", "9000000000000000001")));
        assertThat(segments.render("BUSINESS_FIELD", Map.of("fieldKey", "mdm.materialCategory.code"), context))
                .isEqualTo("CODE_RULE_IT");
        jdbc.update("UPDATE mdm_material_category SET status='DISABLED' WHERE code='CODE_RULE_IT'");
        assertThatThrownBy(() -> segments.render("BUSINESS_FIELD",
                Map.of("fieldKey", "mdm.materialCategory.code"), context)).isInstanceOf(CodeSegmentException.class);
        jdbc.update("DELETE FROM mdm_material_category WHERE code='CODE_RULE_IT'");
    }

    @Test void lifecyclePreservesImmutableHistoryAndVersionConflicts() {
        var first = rules.create("it.code.life", "测试场景", null, definition());
        var enabled = rules.enable(first.id(), first.ruleVersion());
        assertThat(enabled.status()).isEqualTo("ENABLED");
        assertThatThrownBy(() -> rules.updateDraft(first.id(), "测试场景", null, definition(), enabled.ruleVersion()))
                .isInstanceOf(CodeRuleFailure.class);
        var draft = rules.revise(first.id(), enabled.ruleVersion());
        assertThat(draft.revisionNo()).isEqualTo(2);
        assertThatThrownBy(() -> rules.revise(first.id(), draft.ruleVersion())).isInstanceOf(CodeRuleFailure.class);
        var switched = rules.enable(draft.id(), draft.ruleVersion());
        assertThat(rules.detail(first.id()).status()).isEqualTo("DISABLED");
        assertThat(switched.status()).isEqualTo("ENABLED");
        assertThatThrownBy(() -> rules.enable(first.id(), draft.ruleVersion()))
                .isInstanceOf(CodeRuleFailure.class);
        var returned = rules.enable(first.id(), switched.ruleVersion());
        assertThat(returned.status()).isEqualTo("ENABLED");
        assertThat(rules.detail(draft.id()).status()).isEqualTo("DISABLED");
    }

    @Test void partialDraftIsAllowedButSuppliedInvalidValuesAndIncompletePublishAreRejected() {
        var draft = rules.create("it.code.partial", "测试场景", null, new CodeRuleDefinition(1, List.of()));
        assertThatThrownBy(() -> rules.enable(draft.id(), draft.ruleVersion())).isInstanceOf(CodeRuleFailure.class);
        var invalid = new CodeRuleDefinition(1, List.of(new CodeRuleDefinition.Segment("seq", "SEQUENCE",
                Map.of("width", 19))));
        assertThatThrownBy(() -> rules.updateDraft(draft.id(), "测试场景", null, invalid, draft.ruleVersion()))
                .isInstanceOf(CodeRuleFailure.class);
        assertThat(rules.detail(draft.id()).definition()).isEqualTo(new CodeRuleDefinition(1, List.of()));
    }

    @Test void removedProviderLeavesHistoryReadableButPreventsEnable() {
        var initial = rules.create("it.code.removed", "测试场景", null, definition());
        var withoutProvider = new io.github.chrisshi.mom.coderule.application.DefaultCodeRuleApplication(
                store, segments, new CodeFieldRegistry(List.of()), context.getBean(io.github.chrisshi.mom.core.id.IdGenerator.class));
        assertThat(withoutProvider.detail(initial.id()).definition()).isEqualTo(definition());
        assertThatThrownBy(() -> withoutProvider.enable(initial.id(), 0)).isInstanceOf(CodeRuleFailure.class);
        assertThat(rules.detail(initial.id()).status()).isEqualTo("DRAFT");
    }

    @Test void concurrentRevisionAndEnableUseOneRuleVersion() throws Exception {
        var initial = rules.create("it.code.concurrent", "测试场景", null, definition());
        var enabled = rules.enable(initial.id(), initial.ruleVersion());
        var results = race(() -> rules.revise(enabled.id(), enabled.ruleVersion()),
                () -> rules.revise(enabled.id(), enabled.ruleVersion()));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(store.revisions(enabled.ruleId()).stream().filter(r -> "DRAFT".equals(r.status())).count()).isEqualTo(1);
        var draft = store.revisions(enabled.ruleId()).stream().filter(r -> "DRAFT".equals(r.status())).findFirst().orElseThrow();
        var disabled = rules.disable(enabled.id(), rules.detail(enabled.id()).ruleVersion());
        var published = race(() -> rules.enable(enabled.id(), disabled.ruleVersion()),
                () -> rules.enable(draft.id(), disabled.ruleVersion()));
        assertThat(published).containsExactlyInAnyOrder(true, false);
        assertThat(store.revisions(enabled.ruleId()).stream().filter(r -> "ENABLED".equals(r.status())).count()).isEqualTo(1);
    }

    @Test void failedTargetEnableRollsBackPreviousDisable() {
        var first = rules.create("it.code.rollback", "测试场景", null, definition());
        var active = rules.enable(first.id(), first.ruleVersion());
        var draft = rules.revise(first.id(), active.ruleVersion());
        doThrow(new IllegalStateException("injected target write failure"))
                .when(store).changeStatus(draft.id(), "DRAFT", "ENABLED");
        assertThatThrownBy(() -> rules.enable(draft.id(), draft.ruleVersion()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(rules.detail(first.id()).status()).isEqualTo("ENABLED");
        assertThat(rules.detail(draft.id()).status()).isEqualTo("DRAFT");
        assertThat(rules.detail(draft.id()).ruleVersion()).isEqualTo(draft.ruleVersion());
    }

    private static List<Boolean> race(Callable<CodeRuleView> first, Callable<CodeRuleView> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task1 = () -> result(start, first);
            Callable<Boolean> task2 = () -> result(start, second);
            Future<Boolean> a = executor.submit(task1);
            Future<Boolean> b = executor.submit(task2);
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }
    private static boolean result(CountDownLatch start, Callable<CodeRuleView> action) throws Exception {
        start.await(); actor();
        try { action.call(); return true; }
        catch (CodeRuleFailure failure) { return false; }
        finally { SecurityContextHolder.clearContext(); }
    }

    @Test void ruleMetadataIsSharedAcrossRevisionsAndProtectedByDraftAndVersion() {
        var first = rules.create("it.code.metadata", "  物料主数据编码  ", "  创建物料时使用  ", definition());
        assertThat(first.usageScenario()).isEqualTo("物料主数据编码");
        assertThat(first.description()).isEqualTo("创建物料时使用");
        var enabled = rules.enable(first.id(), first.ruleVersion());
        assertThatThrownBy(() -> rules.updateDraft(first.id(), "非法修改", null, definition(), enabled.ruleVersion()))
                .isInstanceOf(CodeRuleFailure.class);
        var draft = rules.revise(first.id(), enabled.ruleVersion());
        assertThat(draft.usageScenario()).isEqualTo(first.usageScenario());
        var saved = rules.updateDraft(draft.id(), "物料分类规则", "说明已补充", draft.definition(), draft.ruleVersion());
        assertThat(saved.ruleVersion()).isEqualTo(draft.ruleVersion() + 1);
        assertThat(rules.detail(first.id()).usageScenario()).isEqualTo("物料分类规则");
        assertThat(rules.detail(first.id()).definition()).isEqualTo(first.definition());
        assertThat(rules.search(new PageQuery<>(new CodeRulePageParams(first.ruleCode(), null), 1, 20)).records())
                .extracting(CodeRuleView::description).containsOnly("说明已补充");
        assertThatThrownBy(() -> rules.updateDraft(draft.id(), "过期请求", null, draft.definition(), draft.ruleVersion()))
                .isInstanceOf(CodeRuleFailure.class);
        assertThat(rules.detail(draft.id()).usageScenario()).isEqualTo(saved.usageScenario());
        doThrow(new IllegalStateException("模拟定义写入失败")).when(store).updateDraft(draft.id(), draft.definition());
        assertThatThrownBy(() -> rules.updateDraft(draft.id(), "应回滚的场景", null, draft.definition(), saved.ruleVersion()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(rules.detail(draft.id()).description()).isEqualTo(saved.description());
        assertThat(rules.detail(draft.id()).usageScenario()).isEqualTo(saved.usageScenario());
        assertThat(rules.detail(draft.id()).ruleVersion()).isEqualTo(saved.ruleVersion());
    }

    @Test void invalidMetadataFailsWithoutCreatingRuleIdentity() {
        for (String invalid : new String[]{null, "", "  ", "场".repeat(121), "bad\0text"}) {
            assertThatThrownBy(() -> rules.create("it.code.invalidmetadata", invalid, null, definition()))
                    .isInstanceOf(CodeRuleFailure.class);
        }
        assertThatThrownBy(() -> rules.create("it.code.invalidmetadata", "有效场景", "字".repeat(1001), definition()))
                .isInstanceOf(CodeRuleFailure.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mdm_code_rule WHERE rule_code='it.code.invalidmetadata'", Long.class)).isZero();
        var boundary = rules.create("it.code.boundary", "场".repeat(120), "字".repeat(1000), definition());
        assertThat(boundary.usageScenario()).hasSize(120);
        assertThat(boundary.description()).hasSize(1000);
    }

    @Test void v130UpgradePreservesLegacyRulesAndDefinitionsWithoutInventingUsage() {
        String schema = "code_rule_metadata_upgrade";
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                PG.getJdbcUrl() + "&currentSchema=" + schema, PG.getUsername(), PG.getPassword());
        var isolated = new JdbcTemplate(source);
        try {
            org.flywaydb.core.Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration/mdm").target("130").load().migrate();
            isolated.update("INSERT INTO mdm_code_rule (id,rule_code,version,created_at,created_by,updated_at,updated_by) VALUES ('9000000000000000010','legacy.metadata',7,CURRENT_TIMESTAMP,'legacy',CURRENT_TIMESTAMP,'legacy')");
            isolated.update("INSERT INTO mdm_code_rule_revision (id,rule_id,revision_no,status,definition,created_at,created_by,updated_at,updated_by) VALUES ('9000000000000000011','9000000000000000010',1,'ENABLED',?::jsonb,CURRENT_TIMESTAMP,'legacy',CURRENT_TIMESTAMP,'legacy')", json.writeValueAsString(definition()));
            String originalColumns = "id,rule_code,version,created_at,created_by,updated_at,updated_by";
            var originalRules = isolated.queryForList("SELECT " + originalColumns + " FROM mdm_code_rule ORDER BY id");
            var originalRevisions = isolated.queryForList("SELECT * FROM mdm_code_rule_revision ORDER BY id");
            org.flywaydb.core.Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration/mdm").load().migrate();
            assertThat(isolated.queryForList("SELECT " + originalColumns + " FROM mdm_code_rule ORDER BY id")).isEqualTo(originalRules);
            assertThat(isolated.queryForList("SELECT * FROM mdm_code_rule_revision ORDER BY id")).isEqualTo(originalRevisions);
            assertThat(isolated.queryForMap("SELECT usage_scenario,description FROM mdm_code_rule WHERE rule_code='legacy.metadata'").values()).containsOnlyNulls();
        } finally { jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }

    @Test void httpPersistsUsageScenarioAndDescription() throws Exception {
        SecurityContextHolder.clearContext();
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        tokens.store("metadata-writer", new MomTokenPrincipal("metadata-writer", List.of("mdm:code-rule:write", "mdm:code-rule:read"), Instant.now().plusSeconds(300)));
        mvc.perform(post("/code-rules").header(HttpHeaders.AUTHORIZATION, "Bearer metadata-writer")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "ruleCode", "it.code.metadata.http", "usageScenario", "物料主数据编码", "description", "新增物料时使用", "definition", definition()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.usageScenario").value("物料主数据编码"))
                .andExpect(jsonPath("$.data.description").value("新增物料时使用"));
    }

    @Test void httpUsesRealRedisTokenAndPermissions() throws Exception {
        SecurityContextHolder.clearContext();
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mvc.perform(get("/code-rules/segments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/code-rules/segments").header(HttpHeaders.AUTHORIZATION, "Bearer unknown"))
                .andExpect(status().isUnauthorized());
        redis.opsForValue().set("mom:token:" + MomTokenFingerprint.of("expired"),
                json.writeValueAsString(new MomTokenPrincipal("expired-user", List.of("mdm:code-rule:read"),
                        Instant.now().minusSeconds(10))), Duration.ofMinutes(1));
        mvc.perform(get("/code-rules/segments").header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                .andExpect(status().isUnauthorized());
        tokens.store("read-only", new MomTokenPrincipal("reader", List.of("mdm:code-rule:read"),
                Instant.now().plusSeconds(300)));
        tokens.store("writer", new MomTokenPrincipal("writer", List.of("mdm:code-rule:write", "mdm:code-rule:read"),
                Instant.now().plusSeconds(300)));
        tokens.store("catalog-only", new MomTokenPrincipal("catalog-reader", List.of(),
                Instant.now().plusSeconds(300)));
        mvc.perform(get("/code-rules/segments").header(HttpHeaders.AUTHORIZATION, "Bearer catalog-only"))
                .andExpect(status().isOk());
        mvc.perform(get("/code-rules/segments").header(HttpHeaders.AUTHORIZATION, "Bearer read-only"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.type=='BUSINESS_FIELD')].parameterSchema.fields[0].options[0].value")
                        .value("mdm.materialCategory.code"));
        String body = "{\"ruleCode\":\"it.code.http\",\"usageScenario\":\"HTTP 测试\",\"definition\":{\"schemaVersion\":1,\"segments\":[]}}";
        mvc.perform(post("/code-rules").header(HttpHeaders.AUTHORIZATION, "Bearer read-only")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/code-rules/search").header(HttpHeaders.AUTHORIZATION, "Bearer writer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{},\"pageNo\":9223372036854775807,\"pageSize\":10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/code-rules").header(HttpHeaders.AUTHORIZATION, "Bearer writer")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT created_by FROM mdm_code_rule WHERE rule_code='it.code.http'", String.class))
                .isEqualTo("writer");
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("test redis outage"))
                .when(tokens).find("writer");
        assertThatThrownBy(() -> mvc.perform(get("/code-rules/segments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer writer")))
                .isInstanceOf(org.springframework.data.redis.RedisConnectionFailureException.class);
    }
}
