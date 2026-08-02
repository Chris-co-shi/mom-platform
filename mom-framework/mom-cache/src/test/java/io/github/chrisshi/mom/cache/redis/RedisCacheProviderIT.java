package io.github.chrisshi.mom.cache.redis;

import io.github.chrisshi.mom.cache.api.CacheEntryKey;
import io.github.chrisshi.mom.cache.api.CacheRegion;
import io.github.chrisshi.mom.cache.api.CacheScope;
import io.github.chrisshi.mom.cache.api.CacheService;
import io.github.chrisshi.mom.cache.api.CacheValueType;
import io.github.chrisshi.mom.cache.config.CaffeineCacheProperties;
import io.github.chrisshi.mom.cache.core.CacheMetrics;
import io.github.chrisshi.mom.cache.core.CachePolicyRegistry;
import io.github.chrisshi.mom.cache.core.DefaultCacheService;
import io.github.chrisshi.mom.cache.core.MultiLevelCacheProvider;
import io.github.chrisshi.mom.cache.local.CaffeineCacheManager;
import io.github.chrisshi.mom.cache.local.CaffeineCacheProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用真实 Redis 验证 typed Cache 的 TTL、损坏数据清理、故障回源与连接恢复。
 *
 * <p>测试使用固定 Redis 8.4.4 官方镜像和有界 Lettuce command timeout。Docker 不可用时仅本地跳过，
 * 独立 redis_cache CI 必须提供 Docker。测试暂停的是自己创建的容器，不影响仓库其他 Redis 验收；所有
 * 连接与容器均在 finally 释放。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisCacheProviderIT {

    private static final CacheRegion<ExampleValue> REGION = new CacheRegion<>(
            "system",
            "dictionary",
            1,
            CacheValueType.of("system.dictionary", 1, ExampleValue.class),
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            true,
            true
    );
    private static final CacheEntryKey<ExampleValue> KEY = CacheEntryKey.of(
            REGION,
            CacheScope.global(),
            "material-type"
    );

    /**
     * 验证真实 L2 命中会恢复精确类型、保留 TTL，并回填被清空的 L1。
     */
    @Test
    void shouldRoundTripWithTtlAndRefreshL1FromRedis() {
        try (GenericContainer<?> redis = redisContainer()) {
            redis.start();
            LettuceConnectionFactory connectionFactory = connectionFactory(redis);
            try {
                Fixture fixture = fixture(connectionFactory);
                fixture.service().put(KEY, new ExampleValue("redis", 7));
                fixture.service().invalidateLocalRegion(REGION);

                ExampleValue restored = fixture.service().get(KEY);
                Long ttl = fixture.redis().getExpire(KEY.build("test"));

                assertThat(restored).isEqualTo(new ExampleValue("redis", 7));
                assertThat(ttl).isPositive().isLessThanOrEqualTo(30);
                assertThat(fixture.metrics().find(CacheMetrics.HIT).tag("layer", "remote").counter().count())
                        .isEqualTo(1.0);
                fixture.redis().delete(KEY.build("test"));
                assertThat(fixture.service().get(KEY)).isEqualTo(restored);
            } finally {
                connectionFactory.destroy();
            }
        }
    }

    /**
     * 验证损坏信封不会变成 Map，且只删除当前精确 Key。
     */
    @Test
    void shouldDeleteExactCorruptedKeyAndReturnMiss() {
        try (GenericContainer<?> redis = redisContainer()) {
            redis.start();
            LettuceConnectionFactory connectionFactory = connectionFactory(redis);
            try {
                Fixture fixture = fixture(connectionFactory);
                String storageKey = KEY.build("test");
                fixture.redis().opsForValue().set(storageKey, "{broken-json", Duration.ofSeconds(30));

                assertThat(fixture.service().get(KEY)).isNull();
                assertThat(fixture.redis().hasKey(storageKey)).isFalse();
                assertThat(fixture.metrics().find(CacheMetrics.ERROR)
                        .tag("operation", "deserialize").counter().count()).isEqualTo(1.0);
            } finally {
                connectionFactory.destroy();
            }
        }
    }

    /**
     * 验证 Redis 暂停时按 Miss 调用数据库 Loader，恢复后同一 Adapter 可再次读写 Redis。
     */
    @Test
    void shouldFallbackDuringRedisTimeoutAndRecoverAfterResume() {
        try (GenericContainer<?> redis = redisContainer()) {
            redis.start();
            LettuceConnectionFactory connectionFactory = connectionFactory(redis);
            boolean paused = false;
            try {
                Fixture fixture = fixture(connectionFactory);
                AtomicInteger loads = new AtomicInteger();
                redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
                paused = true;

                ExampleValue fallback = fixture.service().getOrLoad(
                        KEY,
                        () -> new ExampleValue("database-" + loads.incrementAndGet(), 8));

                assertThat(fallback).isEqualTo(new ExampleValue("database-1", 8));
                assertThat(loads).hasValue(1);
                assertThat(fixture.metrics().find(CacheMetrics.REDIS_TIMEOUT).counter().count())
                        .isGreaterThanOrEqualTo(1.0);

                redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
                paused = false;
                fixture.service().evict(KEY);
                fixture.service().put(KEY, new ExampleValue("recovered", 9));
                fixture.service().invalidateLocalRegion(REGION);

                assertThat(fixture.service().get(KEY)).isEqualTo(new ExampleValue("recovered", 9));
            } finally {
                if (paused) {
                    redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
                }
                connectionFactory.destroy();
            }
        }
    }

    private static GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:8.4.4-alpine"))
                .withExposedPorts(6379);
    }

    private static LettuceConnectionFactory connectionFactory(GenericContainer<?> redis) {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(300))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(standalone, client);
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    private static Fixture fixture(LettuceConnectionFactory connectionFactory) {
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CacheMetrics metrics = new CacheMetrics(meterRegistry);
        CaffeineCacheProvider local = new CaffeineCacheProvider(new CaffeineCacheManager(
                new CaffeineCacheProperties(100, Duration.ofSeconds(5))));
        RedisCacheProvider remote = new RedisCacheProvider(
                redis,
                new JacksonCacheSerializer(JsonMapper.builder().build()),
                metrics);
        CacheService service = new DefaultCacheService(
                new CachePolicyRegistry(),
                new MultiLevelCacheProvider(List.of(remote, local), metrics),
                "test",
                metrics);
        return new Fixture(service, redis, meterRegistry);
    }

    private record Fixture(CacheService service, StringRedisTemplate redis, SimpleMeterRegistry metrics) {
    }

    private record ExampleValue(String source, int version) {
    }
}
