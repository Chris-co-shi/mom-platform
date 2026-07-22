package io.github.chrisshi.mom.security.autoconfigure;

import io.github.chrisshi.mom.security.token.MomSecurityClaims;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 业务服务 Resource Server 的 Issuer、JWK、Audience 与公开路径配置。 */
@ConfigurationProperties("mom.security.resource-server")
public class MomResourceServerProperties {
    private boolean enabled;
    private String issuerUri = "http://127.0.0.1:20100";
    private String jwkSetUri = "http://127.0.0.1:20100/oauth2/jwks";
    private Set<String> acceptedAudiences = new LinkedHashSet<>(MomSecurityClaims.publicClientIds());
    private List<String> publicPaths = List.of("/actuator/health/**", "/actuator/info", "/error");

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
    public List<String> getPublicPaths() { return publicPaths; }
    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }

    public void validate() {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException("Resource Server issuer-uri 不能为空");
        }
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            throw new IllegalStateException("Resource Server jwk-set-uri 不能为空");
        }
        if (acceptedAudiences == null || acceptedAudiences.isEmpty()) {
            throw new IllegalStateException("Resource Server accepted-audiences 不能为空");
        }
    }
}
