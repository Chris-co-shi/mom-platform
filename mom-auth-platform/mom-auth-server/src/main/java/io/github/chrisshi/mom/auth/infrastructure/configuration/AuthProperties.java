package io.github.chrisshi.mom.auth.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "mom.auth")
/** Auth 模块运行时配置，当前只承载 Opaque Token 的有效期。 */
public class AuthProperties {

    /** 新签发访问令牌的有效期，必须为正时长。 */
    private Duration accessTokenTtl = Duration.ofHours(8);

    /**
     * @return 新签发访问令牌的有效期
     */
    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * 设置访问令牌有效期；空值、零和负数会被拒绝，避免签发立即失效的令牌。
     *
     * @param accessTokenTtl 访问令牌有效期
     */
    public void setAccessTokenTtl(Duration accessTokenTtl) {
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("mom.auth.access-token-ttl must be positive");
        }
        this.accessTokenTtl = accessTokenTtl;
    }
}
