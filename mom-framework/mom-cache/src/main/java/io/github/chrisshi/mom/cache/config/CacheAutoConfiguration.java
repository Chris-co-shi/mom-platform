package io.github.chrisshi.mom.cache.config;

import io.github.chrisshi.mom.cache.local.CaffeineCacheManager;
import io.github.chrisshi.mom.cache.local.CaffeineCacheProvider;
import io.github.chrisshi.mom.cache.redis.CacheSerializer;
import io.github.chrisshi.mom.cache.redis.RedisCacheProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto configuration for MOM cache infrastructure.
 */
@AutoConfiguration
@EnableConfigurationProperties(CaffeineCacheProperties.class)
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
    RedisCacheProvider redisCacheProvider(StringRedisTemplate redisTemplate,
                                           CacheSerializer serializer) {
        return new RedisCacheProvider(redisTemplate, serializer);
    }
}
