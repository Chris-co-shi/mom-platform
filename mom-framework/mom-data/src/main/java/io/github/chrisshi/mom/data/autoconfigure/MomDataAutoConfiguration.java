package io.github.chrisshi.mom.data.autoconfigure;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import io.github.chrisshi.mom.core.security.AuditContext;
import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.data.audit.MomMetaObjectHandler;
import io.github.chrisshi.mom.data.config.MomDataAuditProperties;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/** MOM 关系型数据访问、审计和乐观锁自动配置。 */
@AutoConfiguration(
        afterName = "io.github.chrisshi.mom.security.autoconfigure.MomSecurityActorAutoConfiguration",
        beforeName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnClass(MybatisPlusInterceptor.class)
@EnableConfigurationProperties(MomDataAuditProperties.class)
public class MomDataAutoConfiguration {

    /** 提供可被测试替换的 UTC 时钟。 */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock momUtcClock() { return Clock.systemUTC(); }

    /** 提供显式 Actor 上下文执行器，不自动跨线程传播。 */
    @Bean
    @ConditionalOnMissingBean(AuditContextExecutor.class)
    AuditContextExecutor auditContextExecutor() { return new AuditContextExecutor(); }

    /** 无安全 Provider 时只读取显式 AuditContext，仍不默认 SYSTEM。 */
    @Bean
    @ConditionalOnMissingBean(CurrentActorProvider.class)
    CurrentActorProvider auditContextCurrentActorProvider() { return AuditContext::findCurrentActor; }

    /** 创建服务端受控的审计处理器。 */
    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    @ConditionalOnProperty(prefix = "mom.data.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    MetaObjectHandler momMetaObjectHandler(
            Clock clock,
            CurrentActorProvider actorProvider,
            MomDataAuditProperties properties) {
        return new MomMetaObjectHandler(clock, actorProvider, properties);
    }

    /** 没有应用自定义链时提供空链，乐观锁由后处理器追加。 */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    MybatisPlusInterceptor momMybatisPlusInterceptor() { return new MybatisPlusInterceptor(); }

    /** 对默认或应用自定义链补充且只补充一个乐观锁插件。 */
    @Bean
    static BeanPostProcessor momOptimisticLockerInterceptorPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof MybatisPlusInterceptor interceptor
                        && interceptor.getInterceptors().stream()
                        .noneMatch(OptimisticLockerInnerInterceptor.class::isInstance)) {
                    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
                }
                return bean;
            }
        };
    }
}
