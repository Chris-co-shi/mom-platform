package io.github.chrisshi.mom.ratelimit.autoconfigure;

import io.github.chrisshi.mom.ratelimit.FailClosedRedisRateLimiter;
import io.github.chrisshi.mom.ratelimit.RedisRateLimitFailureWebExceptionHandler;
import io.github.chrisshi.mom.ratelimit.RequestIdentityKeyResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.WebExceptionHandler;

/**
 * Gateway Redis 限流自动配置。
 *
 * <p>仅在响应式 Web 应用并且存在 Spring Cloud Gateway 限流接口时生效，避免 Servlet 服务
 * 因为传递依赖意外创建 Gateway Bean。自动配置安排在 Gateway 官方 Redis 自动配置之后执行，
 * 复用其 Lua 脚本、连接和 {@link RedisRateLimiter}，只增加 MOM 的身份解析与 fail-closed 策略。</p>
 */
@AutoConfiguration(afterName = "org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({KeyResolver.class, RedisRateLimiter.class})
public class RedisRateLimitAutoConfiguration {

    /**
     * 创建 MOM 默认请求身份解析器。
     *
     * <p>Spring Cloud Gateway 5 同时提供 {@code principalNameKeyResolver}。将 MOM Resolver 标记为
     * {@link Primary}，保证框架按类型注入默认 Resolver 时没有歧义；路由仍通过 Bean 名显式引用本实现。</p>
     *
     * @return 供 Gateway 路由通过 SpEL 引用的 KeyResolver
     */
    @Bean(name = "requestIdentityKeyResolver")
    @Primary
    @ConditionalOnMissingBean(name = "requestIdentityKeyResolver")
    KeyResolver requestIdentityKeyResolver() {
        return new RequestIdentityKeyResolver();
    }

    /**
     * 创建 MOM fail-closed Redis 令牌桶。
     *
     * <p>路由通过 Bean 名显式引用该包装器。同时将其标记为 {@link Primary}，满足 Gateway 默认
     * RequestRateLimiter 工厂按类型注入单个 {@code RateLimiter<?>} 的要求；官方 RedisRateLimiter
     * 仍作为内部委托存在，不会被直接用于请求放行判断。</p>
     *
     * @param redisRateLimiter Gateway 官方自动配置创建的 RedisRateLimiter
     * @return 保留官方算法并把异常放行转换为错误信号的 RateLimiter
     */
    @Bean(name = "momFailClosedRedisRateLimiter")
    @Primary
    @ConditionalOnBean(RedisRateLimiter.class)
    @ConditionalOnMissingBean(name = "momFailClosedRedisRateLimiter")
    RateLimiter<RedisRateLimiter.Config> momFailClosedRedisRateLimiter(
            RedisRateLimiter redisRateLimiter) {
        return new FailClosedRedisRateLimiter(redisRateLimiter);
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
