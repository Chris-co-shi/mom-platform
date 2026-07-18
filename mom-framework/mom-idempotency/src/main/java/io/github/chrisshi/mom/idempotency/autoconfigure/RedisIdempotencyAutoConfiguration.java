package io.github.chrisshi.mom.idempotency.autoconfigure;

import io.github.chrisshi.mom.idempotency.IdempotencyGuard;
import io.github.chrisshi.mom.idempotency.RedisIdempotencyGuard;
import io.github.chrisshi.mom.idempotency.RedisIdempotencyKeyFactory;
import io.github.chrisshi.mom.idempotency.RedisIdempotencyProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 幂等保护自动配置。
 *
 * <p>该配置必须在 Spring Boot 4 的 {@link DataRedisAutoConfiguration} 之后执行，确保
 * {@link StringRedisTemplate} 已经创建，再判断是否启用 MOM 幂等能力。模块不自建 Redis 连接，
 * 也不覆盖应用自定义实现，从而保持连接配置、集群模式和凭证管理由应用负责。</p>
 */
@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnBean(StringRedisTemplate.class)
@EnableConfigurationProperties(RedisIdempotencyProperties.class)
public class RedisIdempotencyAutoConfiguration {

    /**
     * 创建统一幂等 Key 工厂。
     *
     * @param environment Spring 环境，用于读取当前应用名称
     * @param properties 幂等配置，用于读取环境命名空间
     * @return 线程安全的 Key 工厂
     */
    @Bean
    @ConditionalOnMissingBean
    RedisIdempotencyKeyFactory redisIdempotencyKeyFactory(
            Environment environment,
            RedisIdempotencyProperties properties) {
        String applicationName = environment.getProperty(
                "spring.application.name",
                "unknown-application");
        return new RedisIdempotencyKeyFactory(properties.getEnvironment(), applicationName);
    }

    /**
     * 创建默认 Redis 幂等保护器。
     *
     * @param redisTemplate 字符串 Redis 模板
     * @param keyFactory Key 命名工厂
     * @param properties 失败策略与默认 TTL
     * @return 默认幂等保护接口实现
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyGuard.class)
    IdempotencyGuard redisIdempotencyGuard(
            StringRedisTemplate redisTemplate,
            RedisIdempotencyKeyFactory keyFactory,
            RedisIdempotencyProperties properties) {
        return new RedisIdempotencyGuard(redisTemplate, keyFactory, properties);
    }
}
