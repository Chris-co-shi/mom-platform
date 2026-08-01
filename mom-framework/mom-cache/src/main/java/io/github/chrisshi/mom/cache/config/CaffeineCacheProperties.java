package io.github.chrisshi.mom.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Caffeine L1 的全局容量与旧 API 默认 TTL 配置。
 *
 * <p>typed Region 的 TTL 由业务 Region 明确声明，{@code expireAfterWrite} 只为旧 CacheType 保留。参数可由
 * 部署环境覆盖，不属于 Framework Frozen Contract。记录不可变，可安全跨线程共享。</p>
 *
 * @param maximumSize 每个 Region 的最大条目数
 * @param expireAfterWrite 旧 API 默认写后过期时间
 */
@ConfigurationProperties(prefix = "mom.cache.caffeine")
public record CaffeineCacheProperties(
        long maximumSize,
        Duration expireAfterWrite
) {

    public CaffeineCacheProperties {
        if (maximumSize <= 0) {
            maximumSize = 10000;
        }
        if (expireAfterWrite == null) {
            expireAfterWrite = Duration.ofMinutes(10);
        }
    }
}
