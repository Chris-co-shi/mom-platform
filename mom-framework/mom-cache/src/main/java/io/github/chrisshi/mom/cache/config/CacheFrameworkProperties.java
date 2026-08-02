package io.github.chrisshi.mom.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MOM Cache Framework 的部署环境配置。
 *
 * <p>environment 参与所有 typed L1/L2 Key，防止共享 Redis 时跨环境碰撞。值必须符合 CacheEntryKey 的安全
 *段规则；默认 local 仅服务本地开发，生产部署应显式覆盖。记录不可变，可跨线程共享。</p>
 *
 * @param environment 规范化部署环境名
 */
@ConfigurationProperties(prefix = "mom.cache")
public record CacheFrameworkProperties(String environment) {

    public CacheFrameworkProperties {
        if (environment == null || environment.isBlank()) {
            environment = "local";
        }
    }
}
