package io.github.chrisshi.mom.openfeign.autoconfigure;

import feign.RequestInterceptor;
import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.core.context.CorrelationHeaders;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Feign 同步调用的关联标识自动配置。
 *
 * <p>拦截器从 Servlet 请求线程的 {@link CorrelationContext} 读取关联标识，并写入下游 HTTP Header。
 * 该模块只负责同步 Feign 调用，不处理 WebFlux、异步线程池或消息队列上下文；这些场景必须采用各自
 * 的显式传播机制。</p>
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class CorrelationIdFeignAutoConfiguration {

    /**
     * 创建 MOM 关联标识 Feign 拦截器。
     *
     * <p>当前线程没有绑定关联标识时不主动生成，避免在脱离请求上下文的后台任务中制造无法追溯的
     * 子链路。应用可声明同名 Bean 覆盖默认实现。</p>
     *
     * @return 写入 {@code X-Correlation-Id} 的 Feign RequestInterceptor
     */
    @Bean
    @ConditionalOnMissingBean(name = "momCorrelationIdFeignRequestInterceptor")
    RequestInterceptor momCorrelationIdFeignRequestInterceptor() {
        return template -> {
            String correlationId = CorrelationContext.currentId();
            if (correlationId != null && !correlationId.isBlank()) {
                template.header(CorrelationHeaders.CORRELATION_ID, correlationId);
            }
        };
    }
}
