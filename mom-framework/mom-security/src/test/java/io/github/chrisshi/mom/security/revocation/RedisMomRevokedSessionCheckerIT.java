package io.github.chrisshi.mom.security.revocation;

import io.github.chrisshi.mom.security.revocation.infrastructure.RedisMomRevokedSessionChecker;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resource Server revoked sid 查询的真实 Redis 集成测试。
 *
 * <p>测试使用固定 Redis 8.4.4 镜像和随机映射端口，验证未撤销、已撤销以及数据源不可用时
 * Fail Closed。测试只写独立测试 Key，不修改 IAM 的写入、TTL 或 Session 事务语义；Docker
 * 不可用时本地允许跳过，CI 的 S10 Redis 验收必须提供 Docker。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisMomRevokedSessionCheckerIT {

    private static final String KEY_PREFIX = "mom:test:iam:revoked:sid:";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.4.4-alpine"))
            .withExposedPorts(6379);

    /** 验证检查器读取同一命名空间，且只把存在的 sid 判定为已撤销。 */
    @Test
    void shouldReadRevocationFromAuthoritativeRedisNamespace() {
        LettuceConnectionFactory connectionFactory = createConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379), Duration.ofSeconds(1));
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            RedisMomRevokedSessionChecker checker =
                    new RedisMomRevokedSessionChecker(redis, KEY_PREFIX);

            assertFalse(checker.isRevoked("active-session"));
            redis.opsForValue().set(
                    MomRevokedSessionKeys.key(KEY_PREFIX, "revoked-session"),
                    "1",
                    Duration.ofMinutes(5));
            assertTrue(checker.isRevoked("revoked-session"));
            assertFalse(checker.isRevoked("active-session"));
        }
        finally {
            connectionFactory.destroy();
        }
    }

    /** 验证 Redis 连接失败不会被解释为 Session 仍有效。 */
    @Test
    void shouldFailClosedWhenRedisIsUnavailable() {
        LettuceConnectionFactory connectionFactory = createConnectionFactory(
                "127.0.0.1", 1, Duration.ofMillis(200));
        try {
            RedisMomRevokedSessionChecker checker = new RedisMomRevokedSessionChecker(
                    new StringRedisTemplate(connectionFactory), KEY_PREFIX);

            assertThrows(
                    MomRevocationStoreUnavailableException.class,
                    () -> checker.isRevoked("session-with-unknown-state"));
        }
        finally {
            connectionFactory.destroy();
        }
    }

    /** 创建带有有界命令超时的独立连接工厂，确保失败测试不会无界等待。 */
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
}
