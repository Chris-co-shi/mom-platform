package io.github.chrisshi.mom.webmvc.autoconfigure;

import io.github.chrisshi.mom.webmvc.context.CorrelationIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Servlet Web 应用的关联标识自动配置。
 *
 * <p>仅在 Servlet 应用中注册 {@link CorrelationIdFilter}，不会进入 Gateway 的 WebFlux Classpath。
 * 这种隔离避免 Framework 为了复用请求上下文而让 Gateway 引入 Servlet API 或 Spring MVC。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CorrelationWebMvcAutoConfiguration {

    /**
     * 创建默认关联标识 Filter。
     *
     * <p>应用声明自定义 {@link CorrelationIdFilter} 时自动配置退让，便于后续接入租户、审计或专用
     * Header 校验策略。</p>
     *
     * @return 请求关联标识 Filter
     */
    @Bean
    @ConditionalOnMissingBean(CorrelationIdFilter.class)
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
