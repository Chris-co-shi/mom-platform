package io.github.chrisshi.mom.ratelimit.autoconfigure;

import io.github.chrisshi.mom.ratelimit.RedisRateLimitFailureWebExceptionHandler;
import io.github.chrisshi.mom.ratelimit.RequestIdentityKeyResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebExceptionHandler;

/**
 * Gateway Redis 限流自动配置。
 *
 * <p>仅在响应式 Web 应用并且存在 Spring Cloud Gateway 限流接口时生效，避免 Servlet 服务
 * 因为传递依赖意外创建 Gateway Bean。应用可以声明同名 KeyResolver 或自定义异常处理器覆盖默认策略。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(KeyResolver.class)
public class RedisRateLimitAutoConfiguration {

    /**
     * 创建默认请求身份解析器。
     *
     * @return 供 Gateway 路由通过 SpEL 引用的 KeyResolver
     */
    @Bean(name = "requestIdentityKeyResolver")
    @ConditionalOnMissingBean(name = "requestIdentityKeyResolver")
    KeyResolver requestIdentityKeyResolver() {
        return new RequestIdentityKeyResolver();
    }

    /**
     * 创建 Redis 限流故障处理器，当前固定采用 fail-closed 并返回 503。
     *
     * @return Gateway WebExceptionHandler
     */
    @Bean
    @ConditionalOnMissingBean(RedisRateLimitFailureWebExceptionHandler.class)
    WebExceptionHandler redisRateLimitFailureWebExceptionHandler() {
        return new RedisRateLimitFailureWebExceptionHandler();
    }
}
