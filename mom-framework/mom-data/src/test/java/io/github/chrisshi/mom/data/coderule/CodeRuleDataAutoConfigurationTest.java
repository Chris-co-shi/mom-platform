package io.github.chrisshi.mom.data.coderule;

import io.github.chrisshi.mom.core.codesegment.*;
import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.data.page.PageAdapter;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 公共 Handler 扫描与重复注册的启动失败行为。 */
class CodeRuleDataAutoConfigurationTest {
    private ApplicationContextRunner runner() {
        DataSource source = mock(DataSource.class);
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CodeRuleDataAutoConfiguration.class))
                .withPropertyValues("mom.code-rule.enabled=true", "mom.code-rule.owner=mdm")
                .withBean(DataSource.class, () -> source)
                .withBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(source))
                .withBean(JsonMapper.class, () -> mock(JsonMapper.class))
                .withBean(PageAdapter.class, () -> new PageAdapter(50))
                .withBean(CurrentActorProvider.class, () -> Optional::empty)
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(IdGenerator.class, () -> () -> "1");
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
        CodeSegmentHandler duplicate = new io.github.chrisshi.mom.data.coderule.handler.LiteralCodeSegmentHandler();
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
