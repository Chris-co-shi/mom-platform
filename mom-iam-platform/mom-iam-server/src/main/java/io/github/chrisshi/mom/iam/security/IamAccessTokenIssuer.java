package io.github.chrisshi.mom.iam.security;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import java.time.Instant;
import java.util.*;

/**
 * 使用 IAM 现有 RSA JWK 与统一 Claims 规则签发内部 Access Token。
 *
 * <p>该组件不加载账号或授权数据，不创建 Session/Refresh，也不构造伪造的 OAuth2 Grant Context。
 * 它复用 Spring Security 的 {@link JwtEncoder}、既有 {@code kid}、RS256、Issuer 与调用方给出的绝对
 * 期限；签名基础设施不可用时异常向上传播并失败关闭。</p>
 */
public final class IamAccessTokenIssuer {
    private final JwtEncoder encoder;
    private final AuthorizationServerSettings settings;
    private final IamAuthorizationProperties authorizationProperties;
    private final IamJwtClaimsAssembler claimsAssembler;

    /**
     * 创建复用现有签名基础设施的 Access Token 签发器。
     */
    public IamAccessTokenIssuer(
        JwtEncoder encoder,
        AuthorizationServerSettings settings,
        IamAuthorizationProperties authorizationProperties,
        IamJwtClaimsAssembler claimsAssembler) {
        this.encoder = encoder;
        this.settings = settings;
        this.authorizationProperties = authorizationProperties;
        this.claimsAssembler = claimsAssembler;
    }

    /**
     * 签发带权威 sid 和授权 Claims 的 JWT。
     *
     * @param context   IAM 权威授权快照
     * @param sessionId 权威 Session ID
     * @param clientId  已校验 Client ID
     * @param issuedAt  签发时间
     * @param expiresAt 访问令牌绝对过期时间
     * @param scopes    已批准 Scope
     * @return 协议无关的内部签发结果
     */
    public IssuedAccessToken issue(
        IamAuthorizationContext context,
        String sessionId,
        String clientId,
        Instant issuedAt,
        Instant expiresAt,
        Set<String> scopes) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer(Objects.requireNonNull(settings.getIssuer()))
            .audience(List.of(clientId))
            .issuedAt(issuedAt)
            .notBefore(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString());
        claimsAssembler.applyIdentity(claims, context, clientId);
        claimsAssembler.applyAccessAuthorization(claims, context, sessionId);
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(authorizationProperties.getKey().getKeyId())
                .build(),
            claims.build()));
        Set<String> orderedScopes = Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
        return new IssuedAccessToken(jwt.getTokenValue(), issuedAt, expiresAt, orderedScopes);
    }

    /**
     * 内部签发结果；不包含 Refresh Token、HTTP 响应或 OAuth2 Request Context。
     */
    public record IssuedAccessToken(
        String tokenValue,
        Instant issuedAt,
        Instant expiresAt,
        Set<String> scopes) {
    }
}
