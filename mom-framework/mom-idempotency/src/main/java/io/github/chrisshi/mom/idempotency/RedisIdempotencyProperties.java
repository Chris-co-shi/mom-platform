package io.github.chrisshi.mom.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis 幂等保护配置。
 *
 * <p>属性前缀为 {@code mom.idempotency.redis}。默认采用 fail-closed，避免 Redis 故障时继续执行
 * 可能产生库存、批次、消息或外部接口副作用的请求。</p>
 */
@ConfigurationProperties("mom.idempotency.redis")
public class RedisIdempotencyProperties {

    /**
     * Redis Key 中的环境段，用于隔离 local、test、staging 和 production 数据。
     */
    private String environment = "local";

    /**
     * 未由调用方显式提供 TTL 时使用的默认生存时间。
     */
    private Duration defaultTtl = Duration.ofMinutes(10);

    /**
     * Redis 不可用时的处理策略。
     */
    private RedisFailureMode failureMode = RedisFailureMode.FAIL_CLOSED;

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public RedisFailureMode getFailureMode() {
        return failureMode;
    }

    public void setFailureMode(RedisFailureMode failureMode) {
        this.failureMode = failureMode;
    }
}
