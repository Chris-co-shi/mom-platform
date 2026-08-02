package io.github.chrisshi.mom.cache.config;

import io.github.chrisshi.mom.cache.api.CacheProvider;
import io.github.chrisshi.mom.cache.api.CacheService;
import io.github.chrisshi.mom.cache.core.CacheMetrics;
import io.github.chrisshi.mom.cache.core.CachePolicyRegistry;
import io.github.chrisshi.mom.cache.core.DefaultCacheService;
import io.github.chrisshi.mom.cache.core.MultiLevelCacheProvider;
import io.github.chrisshi.mom.cache.local.CaffeineCacheManager;
import io.github.chrisshi.mom.cache.local.CaffeineCacheProvider;
import io.github.chrisshi.mom.cache.redis.CacheSerializer;
import io.github.chrisshi.mom.cache.redis.JacksonCacheSerializer;
import io.github.chrisshi.mom.cache.redis.RedisCacheProvider;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * 装配 MOM typed Cache、Caffeine L1、可选 Redis L2 与 Micrometer 指标。
 *
 * <p>配置只连接公共契约、Core 编排和两个已有 Adapter，不创建动态 Provider Registry。ObjectMapper、
 * StringRedisTemplate 与 MeterRegistry 均复用 Spring Boot 管理的实例；Redis Bean 缺失时仅保留 L1，缓存
 * 失败不会阻止应用启动或业务回源。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties({
        CacheFrameworkProperties.class,
        CaffeineCacheProperties.class,
        RedisCacheProperties.class
})
public class CacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CaffeineCacheManager caffeineCacheManager(CaffeineCacheProperties properties) {
        return new CaffeineCacheManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    CaffeineCacheProvider caffeineCacheProvider(CaffeineCacheManager manager) {
        return new CaffeineCacheProvider(manager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "mom.cache.redis", name = "enabled", matchIfMissing = true)
    RedisCacheProvider redisCacheProvider(StringRedisTemplate redisTemplate,
                                           CacheSerializer serializer,
                                           CacheMetrics metrics) {
        return new RedisCacheProvider(redisTemplate, serializer, metrics);
    }

    @Bean
    @ConditionalOnMissingBean
    CacheSerializer cacheSerializer(ObjectMapper objectMapper) {
        return new JacksonCacheSerializer(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    CacheMetrics cacheMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new CacheMetrics(meterRegistryProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    MultiLevelCacheProvider multiLevelCacheProvider(List<CacheProvider> providers, CacheMetrics metrics) {
        return new MultiLevelCacheProvider(providers, metrics);
    }

    @Bean
    @ConditionalOnMissingBean
    CachePolicyRegistry cachePolicyRegistry() {
        return new CachePolicyRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(CacheService.class)
    CacheService cacheService(
            CachePolicyRegistry policyRegistry,
            MultiLevelCacheProvider cacheProvider,
            CacheFrameworkProperties properties,
            CacheMetrics metrics) {
        return new DefaultCacheService(policyRegistry, cacheProvider, properties.environment(), metrics);
    }
}
