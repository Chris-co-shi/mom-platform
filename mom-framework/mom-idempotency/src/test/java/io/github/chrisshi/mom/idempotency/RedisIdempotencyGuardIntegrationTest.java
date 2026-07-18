package io.github.chrisshi.mom.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 幂等保护真实中间件测试。
 *
 * <p>测试使用 Redis 8.4.4 官方镜像，验证 SET NX + TTL 的首次/重复语义，以及 Redis 不可用时
 * fail-closed 与 fail-open 的差异。Docker 不可用时跳过，GitHub Actions 必须提供 Docker 并执行该测试。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisIdempotencyGuardIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.4.4-alpine"))
            .withExposedPorts(6379);

    /**
     * 验证首次请求取得占位、相同请求判定重复，并确认原始业务值不会出现在 Redis Key 中。
     */
    @Test
    void shouldAcquireOnlyOnceAndProtectRawBusinessKey() {
        try (LettuceConnectionFactory connectionFactory = createConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379),
                Duration.ofSeconds(1))) {
            RedisIdempotencyGuard guard = createGuard(
                    connectionFactory,
                    RedisFailureMode.FAIL_CLOSED);
            Duration ttl = Duration.ofSeconds(30);

            IdempotencyAcquireResult first = guard.tryAcquire(
                    "integration.delivery.receive",
                    "supplier-order-20260718-0001",
                    "correlation-first",
                    ttl);
            IdempotencyAcquireResult second = guard.tryAcquire(
                    "integration.delivery.receive",
                    "supplier-order-20260718-0001",
                    "correlation-retry",
                    ttl);

            assertEquals(IdempotencyAcquireStatus.ACQUIRED, first.status());
            assertEquals(IdempotencyAcquireStatus.DUPLICATE, second.status());
            assertTrue(first.mayProceed());
            assertFalse(second.mayProceed());
            assertFalse(first.protectedKey().contains("supplier-order-20260718-0001"));
            assertTrue(first.protectedKey().startsWith(
                    "mom:test:mom-idempotency-test:idempotency:integration.delivery.receive:"));
        }
    }

    /**
     * 验证 Redis 不可用时默认 fail-closed，调用方不会获得继续执行业务的资格。
     */
    @Test
    void shouldFailClosedWhenRedisIsUnavailable() {
        try (LettuceConnectionFactory connectionFactory = createConnectionFactory(
                "127.0.0.1",
                1,
                Duration.ofMillis(200))) {
            RedisIdempotencyGuard guard = createGuard(
                    connectionFactory,
                    RedisFailureMode.FAIL_CLOSED);

            assertThrows(IdempotencyUnavailableException.class, () -> guard.tryAcquire(
                    "integration-probe",
                    "request-closed",
                    "correlation-closed",
                    Duration.ofSeconds(30)));
        }
    }

    /**
     * 验证显式配置 fail-open 时返回 BYPASSED，不能伪装成正常 ACQUIRED。
     */
    @Test
    void shouldMarkBypassedWhenFailOpenIsConfigured() {
        try (LettuceConnectionFactory connectionFactory = createConnectionFactory(
                "127.0.0.1",
                1,
                Duration.ofMillis(200))) {
            RedisIdempotencyGuard guard = createGuard(
                    connectionFactory,
                    RedisFailureMode.FAIL_OPEN);

            IdempotencyAcquireResult result = guard.tryAcquire(
                    "integration-probe",
                    "request-open",
                    "correlation-open",
                    Duration.ofSeconds(30));

            assertEquals(IdempotencyAcquireStatus.BYPASSED, result.status());
            assertTrue(result.mayProceed());
            assertEquals("RedisConnectionFailureException", result.failureReason());
        }
    }

    /**
     * 创建带有短命令超时的 Lettuce 连接工厂，避免故障策略测试长时间阻塞。
     */
    private static LettuceConnectionFactory createConnectionFactory(
            String host,
            int port,
            Duration timeout) {
        RedisStandaloneConfiguration redisConfiguration =
                new RedisStandaloneConfiguration(host, port);
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redisConfiguration, clientConfiguration);
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    /**
     * 使用指定失败策略创建被测幂等保护器。
     */
    private static RedisIdempotencyGuard createGuard(
            LettuceConnectionFactory connectionFactory,
            RedisFailureMode failureMode) {
        RedisIdempotencyProperties properties = new RedisIdempotencyProperties();
        properties.setEnvironment("test");
        properties.setFailureMode(failureMode);
        return new RedisIdempotencyGuard(
                new StringRedisTemplate(connectionFactory),
                new RedisIdempotencyKeyFactory("test", "mom-idempotency-test"),
                properties);
    }
}
