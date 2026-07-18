package io.github.chrisshi.mom.tracing.autoconfigure;

import io.github.chrisshi.mom.tracing.CurrentTraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * MOM Micrometer Trace 上下文自动配置。
 *
 * <p>配置仅在 Micrometer {@link Tracer} 已由 Spring Boot 创建时注册平台访问器，不自行构造 SDK、Exporter
 * 或网络客户端。OTLP 是否导出由 Boot 管理配置决定；Exporter 不可用时不影响该 Bean 创建，也不得阻塞
 * 业务请求。</p>
 */
@AutoConfiguration
@ConditionalOnClass(Tracer.class)
@ConditionalOnBean(Tracer.class)
public class MomTracingAutoConfiguration {

    /**
     * 创建不暴露具体追踪实现的当前上下文访问器。
     *
     * @param tracer Spring Boot 管理的 Micrometer Tracer
     * @return MOM 当前 Trace 上下文访问器
     */
    @Bean
    @ConditionalOnMissingBean
    CurrentTraceContext momCurrentTraceContext(Tracer tracer) {
        return new CurrentTraceContext(tracer);
    }
}
