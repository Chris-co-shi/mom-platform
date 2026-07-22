package io.github.chrisshi.mom.gateway.security;

import io.github.chrisshi.mom.security.token.MomSecurityClaims;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/** Gateway JWT、Audience、revoked sid 与 Fail Closed 配置。 */
@ConfigurationProperties("mom.gateway.security")
public class MomGatewaySecurityProperties {
    private boolean enabled = true;
    private String issuerUri = "http://127.0.0.1:20100";
    private String jwkSetUri = "http://127.0.0.1:20100/oauth2/jwks";
    private Set<String> acceptedAudiences = new LinkedHashSet<>(MomSecurityClaims.publicClientIds());
    private String revokedSidKeyPrefix = "mom:iam:revoked:sid:";
    private Duration redisTimeout = Duration.ofSeconds(2);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
    public String getJwkSetUri() { return jwkSetUri; }
    public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
    public Set<String> getAcceptedAudiences() { return acceptedAudiences; }
    public void setAcceptedAudiences(Set<String> acceptedAudiences) {
        this.acceptedAudiences = acceptedAudiences == null ? new LinkedHashSet<>() : acceptedAudiences;
    }
    public String getRevokedSidKeyPrefix() { return revokedSidKeyPrefix; }
    public void setRevokedSidKeyPrefix(String revokedSidKeyPrefix) {
        this.revokedSidKeyPrefix = revokedSidKeyPrefix;
    }
    public Duration getRedisTimeout() { return redisTimeout; }
    public void setRedisTimeout(Duration redisTimeout) { this.redisTimeout = redisTimeout; }

    public void validate() {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException("Gateway issuer-uri 不能为空");
        }
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalStateException("Gateway jwk-set-uri 不能为空");
        }
        if (acceptedAudiences == null || acceptedAudiences.isEmpty()) {
            throw new IllegalStateException("Gateway accepted-audiences 不能为空");
        }
        if (revokedSidKeyPrefix == null || revokedSidKeyPrefix.isBlank()) {
            throw new IllegalStateException("Gateway revoked sid Key 前缀不能为空");
        }
        if (redisTimeout == null || redisTimeout.isNegative() || redisTimeout.isZero()) {
            throw new IllegalStateException("Gateway Redis 超时必须为正数");
        }
    }
}
