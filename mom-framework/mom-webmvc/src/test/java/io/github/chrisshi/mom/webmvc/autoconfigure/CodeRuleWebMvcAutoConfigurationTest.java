package io.github.chrisshi.mom.webmvc.autoconfigure;

import io.github.chrisshi.mom.coderule.application.CodeRuleApplication;
import io.github.chrisshi.mom.coderule.autoconfigure.CodeRuleAutoConfiguration;
import io.github.chrisshi.mom.coderule.field.CodeFieldRegistry;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry;
import io.github.chrisshi.mom.coderule.store.CodeRuleStore;
import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.webmvc.coderule.CodeRuleController;
import io.github.chrisshi.mom.webmvc.coderule.CodeRuleExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 验证公共模块迁移后的装配顺序和 HTTP 暴露条件。 */
class CodeRuleWebMvcAutoConfigurationTest {
    private WebApplicationContextRunner runner() {
        DataSource source = mock(DataSource.class);
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CodeRuleWebMvcAutoConfiguration.class,
                        CodeRuleAutoConfiguration.class))
                .withPropertyValues("mom.code-rule.enabled=true", "mom.code-rule.owner=mdm",
                        "mom.security.resource-server.enabled=true")
                .withBean(DataSource.class, () -> source)
                .withBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(source))
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withBean(CurrentActorProvider.class, () -> Optional::empty)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(IdGenerator.class, () -> () -> "1")
                .withBean(I18nMessageResolver.class, () -> mock(I18nMessageResolver.class));
    }

    @Test void enabledCreatesExactlyOneControllerAndRegistryAfterApplication() {
        runner().run(context -> assertThat(context).hasNotFailed()
                .hasSingleBean(CodeRuleStore.class).hasSingleBean(CodeRuleApplication.class)
                .hasSingleBean(CodeSegmentRegistry.class).hasSingleBean(CodeFieldRegistry.class)
                .hasSingleBean(CodeRuleController.class).hasSingleBean(CodeRuleExceptionHandler.class));
    }

    @Test void disabledDoesNotExposeApiOrStore() {
        runner().withPropertyValues("mom.code-rule.enabled=false").run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(CodeRuleStore.class)
                        .doesNotHaveBean(CodeRuleController.class).doesNotHaveBean(CodeRuleExceptionHandler.class));
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                CodeRuleWebMvcAutoConfiguration.class, CodeRuleAutoConfiguration.class))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(CodeRuleStore.class)
                        .doesNotHaveBean(CodeRuleController.class));
    }

    @Test void noResourceServerOrNoApplicationDoesNotExposeApi() {
        runner().withPropertyValues("mom.security.resource-server.enabled=false").run(context ->
                assertThat(context).hasNotFailed().hasSingleBean(CodeRuleStore.class)
                        .doesNotHaveBean(CodeRuleController.class));
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(CodeRuleWebMvcAutoConfiguration.class))
                .withPropertyValues("mom.code-rule.enabled=true", "mom.security.resource-server.enabled=true")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(CodeRuleController.class));
    }
}
