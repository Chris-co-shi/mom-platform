package io.github.chrisshi.mom.tracing.autoconfigure;

import io.github.chrisshi.mom.tracing.CurrentTraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * MOM Micrometer Trace 上下文自动配置。
 *
 * <p>配置始终注册平台访问器，并通过 {@link ObjectProvider} 读取 Spring Boot 管理的可选 {@link Tracer}。
 * 数据库迁移、纯离线任务或裁剪测试上下文没有 Tracer 时访问器返回空快照；HTTP、Feign 与消息真实验收则要求
 * 当前 Span 必须存在。该配置不自行构造 SDK、Exporter 或网络客户端。</p>
 */
@AutoConfiguration
@ConditionalOnClass(Tracer.class)
public class MomTracingAutoConfiguration {

    /**
     * 创建不暴露具体追踪实现的当前上下文访问器。
     *
     * @param tracerProvider Spring Boot 管理的可选 Micrometer Tracer Provider
     * @return MOM 当前 Trace 上下文访问器
     */
    @Bean
    @ConditionalOnMissingBean
    CurrentTraceContext momCurrentTraceContext(ObjectProvider<Tracer> tracerProvider) {
        return new CurrentTraceContext(tracerProvider.getIfAvailable());
    }
}
