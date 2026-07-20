package io.github.chrisshi.mom.data.autoconfigure;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.data.config.MomDataAuditProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** 数据审计默认配置、Bean 可替换和乐观锁单实例测试。 */
class MomDataAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MomDataAutoConfiguration.class));

    @Test
    void defaultsShouldEnableFailClosedAuditAndSingleOptimisticLocker() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context).hasSingleBean(AuditContextExecutor.class);
            assertThat(context).hasSingleBean(CurrentActorProvider.class);
            assertThat(context).hasSingleBean(MetaObjectHandler.class);
            MomDataAuditProperties properties = context.getBean(MomDataAuditProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.isFailOnMissingActor()).isTrue();
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
            assertThat(interceptor.getInterceptors().stream()
                    .filter(OptimisticLockerInnerInterceptor.class::isInstance)).hasSize(1);
        });
    }

    @Test
    void applicationBeansShouldReplaceClockAndReuseExistingInterceptorChain() {
        Clock fixed = Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC);
        MybatisPlusInterceptor existing = new MybatisPlusInterceptor();
        contextRunner.withBean(Clock.class, () -> fixed)
                .withBean(MybatisPlusInterceptor.class, () -> existing)
                .run(context -> {
                    assertThat(context.getBean(Clock.class)).isSameAs(fixed);
                    assertThat(context.getBean(MybatisPlusInterceptor.class)).isSameAs(existing);
                    assertThat(existing.getInterceptors().stream()
                            .filter(OptimisticLockerInnerInterceptor.class::isInstance)).hasSize(1);
                });
    }

    @Test
    void auditHandlerMayBeExplicitlyDisabledWithoutChangingMissingActorPolicy() {
        contextRunner.withPropertyValues("mom.data.audit.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(MetaObjectHandler.class);
            assertThat(context.getBean(MomDataAuditProperties.class).isFailOnMissingActor()).isTrue();
        });
    }
}
