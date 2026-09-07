package io.github.chrisshi.mom.gateway.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Gateway 内部 Redis 限流与可信客户端 IP 解析装配。
 *
 * <p>该配置只组装 Gateway 边缘能力，不作为独立 framework 模块发布，也不依赖
 * Auth Token Store 或业务服务。配置对象与解析器都是不可变、线程安全的；Redis 故障继续由
 * {@link FailClosedRedisRateLimiter} 转换为 503，不做放行降级。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TrustedClientIpProperties.class)
public class GatewayRateLimitConfiguration {

    /**
     * 创建可信客户端 IP 解析器。
     *
     * @param properties 可信代理 CIDR 配置
     * @return 线程安全的客户端 IP 解析器
     */
    @Bean
    TrustedClientIpResolver trustedClientIpResolver(TrustedClientIpProperties properties) {
        return new TrustedClientIpResolver(properties);
    }

    /**
     * 创建路由配置引用的 IP 限流 Key 解析器。
     *
     * @param trustedClientIpResolver 可信客户端 IP 解析器
     * @return IP 限流 Key 解析器
     */
    @Bean(name = "requestIdentityKeyResolver")
    @Primary
    KeyResolver requestIdentityKeyResolver(TrustedClientIpResolver trustedClientIpResolver) {
        return new RequestIdentityKeyResolver(trustedClientIpResolver);
    }

    /**
     * 保留 Spring Cloud Gateway 官方 Redis Token Bucket，并将基础设施异常收敛为 fail-closed 503。
     *
     * @param redisRateLimiter 官方 Redis 限流实现
     * @param meterRegistryProvider 可选指标注册表
     * @return fail-closed 限流器
     */
    @Bean(name = "momFailClosedRedisRateLimiter")
    @Primary
    RateLimiter<RedisRateLimiter.Config> momFailClosedRedisRateLimiter(
            RedisRateLimiter redisRateLimiter,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new FailClosedRedisRateLimiter(
                redisRateLimiter,
                meterRegistryProvider.getIfAvailable());
    }
}
