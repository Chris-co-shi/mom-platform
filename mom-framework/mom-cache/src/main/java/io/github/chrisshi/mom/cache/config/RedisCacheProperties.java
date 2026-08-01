package io.github.chrisshi.mom.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis L2 的启用与诊断超时配置。
 *
 * <p>{@code keyPrefix} 仅为旧配置兼容保留，新 typed Key 已包含完整 MOM Namespace。具体 Redis 命令超时由
 * Spring Data Redis 客户端统一配置；这里的建议值不属于 Frozen Contract，可由业务部署覆盖。</p>
 *
 * @param keyPrefix 旧 Key 前缀
 * @param enabled 是否启用 Redis L2 装配
 * @param timeout 建议诊断超时，不覆盖客户端权威配置
 */
@ConfigurationProperties(prefix = "mom.cache.redis")
public record RedisCacheProperties(
        String keyPrefix,
        boolean enabled,
        Duration timeout
) {

    public RedisCacheProperties {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "mom:cache";
        }
        if (timeout == null) {
            timeout = Duration.ofMillis(200);
        }
    }
}
