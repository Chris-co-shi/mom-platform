package io.github.chrisshi.mom.auth.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "mom.auth")
public class AuthProperties {

    private Duration accessTokenTtl = Duration.ofHours(8);

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("mom.auth.access-token-ttl must be positive");
        }
        this.accessTokenTtl = accessTokenTtl;
    }
}
