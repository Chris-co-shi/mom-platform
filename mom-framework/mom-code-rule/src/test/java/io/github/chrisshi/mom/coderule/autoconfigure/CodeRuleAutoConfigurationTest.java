package io.github.chrisshi.mom.coderule.autoconfigure;

import io.github.chrisshi.mom.coderule.field.CodeFieldMetadata;
import io.github.chrisshi.mom.coderule.field.CodeFieldProvider;
import io.github.chrisshi.mom.coderule.field.CodeGenerationContext;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentHandler;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentMetadata;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry;
import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.coderule.application.CodeRuleApplication;
import io.github.chrisshi.mom.coderule.store.CodeRuleStore;
import io.github.chrisshi.mom.coderule.CodeRulePageParams;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageQueryValidationException;
import io.github.chrisshi.mom.coderule.field.CodeFieldRegistry;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentParameterSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 公共 Handler 扫描与重复注册的启动失败行为。 */
class CodeRuleAutoConfigurationTest {
    private ApplicationContextRunner runner() {
        DataSource source = mock(DataSource.class);
        return runner(source, new DataSourceTransactionManager(source));
    }
    private ApplicationContextRunner runner(DataSource source, PlatformTransactionManager transactions) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CodeRuleAutoConfiguration.class))
                .withPropertyValues("mom.code-rule.enabled=true", "mom.code-rule.owner=mdm")
                .withBean(DataSource.class, () -> source)
                .withBean(PlatformTransactionManager.class, () -> transactions)
                .withBean(JsonMapper.class, () -> mock(JsonMapper.class))
                .withBean(CurrentActorProvider.class, () -> Optional::empty)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(IdGenerator.class, () -> () -> "1");
    }
    @Test void disabledOrAbsentDoesNotRequireInfrastructure() {
        var bare = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CodeRuleAutoConfiguration.class));
        bare.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(CodeRuleStore.class)
                    .doesNotHaveBean(CodeRuleApplication.class).doesNotHaveBean(CodeSegmentRegistry.class)
                    .doesNotHaveBean(CodeFieldRegistry.class).doesNotHaveBean(CodeSegmentHandler.class);
        });
        bare.withPropertyValues("mom.code-rule.enabled=false").run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(CodeRuleStore.class)
                        .doesNotHaveBean(CodeRuleApplication.class).doesNotHaveBean(CodeSegmentHandler.class));
    }
    @Test void enabledRequiresOwnerInfrastructure() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CodeRuleAutoConfiguration.class))
                .withPropertyValues("mom.code-rule.enabled=true", "mom.code-rule.owner=mdm")
                .run(context -> assertThat(context).hasFailed());
        runner().withPropertyValues("mom.code-rule.owner=mdm.other")
                .run(context -> assertThat(context).hasFailed());
        runner(mock(DataSource.class), new DataSourceTransactionManager(mock(DataSource.class)))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("编码规则 Store 与本地事务必须使用同一 DataSource"));
    }
    @Test void secondOwnerUsesIndependentCatalogAndStore() {
        runner().withPropertyValues("mom.code-rule.owner=test_owner").run(second -> {
            assertThat(second).hasNotFailed().hasSingleBean(CodeRuleStore.class);
            assertThat(second.getBean(CodeSegmentRegistry.class).catalog(1))
                    .extracting(CodeSegmentMetadata::type).doesNotContain("BUSINESS_FIELD", "CUSTOM");
        });
    }
    @Test void unsafeOwnerFailsBeforeAccessingDataSource() {
        for (String owner : new String[]{"", "MDM", "mdm.other", "mdm;drop table x", "mdm--", "a".repeat(45)}) {
            runner().withPropertyValues("mom.code-rule.owner=" + owner)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
    @Test void paginationUsesExistingPropertyAndRejectsInvalidConfiguration() {
        runner().run(context -> assertThatThrownBy(() -> context.getBean(CodeRuleStore.class)
                .search(new PageQuery<>(new CodeRulePageParams(null, null), 1, 201)))
                .isInstanceOf(PageQueryValidationException.class).hasMessageContaining("200"));
        runner().withPropertyValues("mom.data.pagination.max-page-size=50").run(context ->
                assertThatThrownBy(() -> context.getBean(CodeRuleStore.class)
                        .search(new PageQuery<>(new CodeRulePageParams(null, null), 1, 51)))
                        .isInstanceOf(PageQueryValidationException.class).hasMessageContaining("50"));
        runner().withPropertyValues("mom.data.pagination.max-page-size=0")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void ownerHandlersAndProvidersAreCollectedWithoutRegistryChanges() {
        CodeFieldProvider provider = new CodeFieldProvider() {
            public CodeFieldMetadata metadata() { return new CodeFieldMetadata("owner.code", "字段", "说明", List.of()); }
            public String resolve(CodeGenerationContext context) { return "x"; }
        };
        CodeSegmentHandler custom = new CodeSegmentHandler() {
            public CodeSegmentMetadata metadata() {
                return new CodeSegmentMetadata("CUSTOM", "自定义", "Owner 自定义算法", 1,
                        new CodeSegmentParameterSchema(1, List.of()),
                        CodeSegmentMetadata.Capability.STATELESS_RENDERABLE);
            }
            public void validate(Map<String, ?> parameters) { }
        };
        runner().withBean(CodeFieldProvider.class, () -> provider)
                .withBean("ownerHandler", CodeSegmentHandler.class, () -> custom).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(CodeFieldRegistry.class)
                            .hasSingleBean(CodeSegmentRegistry.class).hasSingleBean(CodeRuleApplication.class)
                            .hasSingleBean(CodeRuleStore.class);
                    assertThat(context.getBean(CodeFieldRegistry.class).require("owner.code")).isSameAs(provider);
                    assertThat(context.getBean(CodeSegmentRegistry.class).catalog(1))
                            .extracting(CodeSegmentMetadata::type)
                            .containsExactlyInAnyOrder("LITERAL", "DATE", "SEQUENCE", "BUSINESS_FIELD", "CUSTOM");
                });
    }
    @Test void noProviderHidesBusinessFieldButKeepsBuiltins() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CodeSegmentRegistry.class).catalog(1))
                    .extracting(CodeSegmentMetadata::type)
                    .containsExactlyInAnyOrder("LITERAL", "DATE", "SEQUENCE");
        });
    }
    @Test void duplicateHandlerAndProviderFailStartup() {
        CodeSegmentHandler duplicate = new io.github.chrisshi.mom.coderule.handler.LiteralCodeSegmentHandler();
        runner().withBean("duplicateLiteral", CodeSegmentHandler.class, () -> duplicate)
                .run(context -> assertThat(context).hasFailed());
        CodeFieldProvider provider = new CodeFieldProvider() {
            public CodeFieldMetadata metadata() { return new CodeFieldMetadata("dup.code", "字段", "说明", List.of()); }
            public String resolve(CodeGenerationContext context) { return "x"; }
        };
        runner().withBean("firstProvider", CodeFieldProvider.class, () -> provider)
                .withBean("secondProvider", CodeFieldProvider.class, () -> provider)
                .run(context -> assertThat(context).hasFailed());
    }
}
