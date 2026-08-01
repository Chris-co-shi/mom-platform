package io.github.chrisshi.mom.cache.config;

import io.github.chrisshi.mom.cache.local.CaffeineCacheManager;
import io.github.chrisshi.mom.cache.local.CaffeineCacheProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto configuration for MOM cache infrastructure.
 */
@AutoConfiguration
@EnableConfigurationProperties(CaffeineCacheProperties.class)
public class CacheAutoConfiguration {

    @Bean
    CaffeineCacheManager caffeineCacheManager(CaffeineCacheProperties properties) {
        return new CaffeineCacheManager(properties);
    }

    @Bean
    CaffeineCacheProvider caffeineCacheProvider(CaffeineCacheManager manager) {
        return new CaffeineCacheProvider(manager);
    }
}
