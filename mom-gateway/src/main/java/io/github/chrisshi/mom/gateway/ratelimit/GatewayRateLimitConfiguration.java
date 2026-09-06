package io.github.chrisshi.mom.gateway.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Gateway 内部 Redis 限流配置，不再作为独立 framework 模块发布。 */
@Configuration(proxyBeanMethods = false)
public class GatewayRateLimitConfiguration {

    @Bean(name = "requestIdentityKeyResolver")
    @Primary
    KeyResolver requestIdentityKeyResolver() {
        return new RequestIdentityKeyResolver();
    }

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
