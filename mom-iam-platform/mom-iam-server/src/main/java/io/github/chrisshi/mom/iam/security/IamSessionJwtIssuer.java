package io.github.chrisshi.mom.iam.security;

import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/** 为 Refresh Rotation 结果签发带 sid 的 10 分钟 JWT Access Token。 */
public final class IamSessionJwtIssuer {
    private final JwtEncoder encoder;
    private final AuthorizationServerSettings settings;
    private final IamAuthorizationProperties authorizationProperties;

    public IamSessionJwtIssuer(
            JwtEncoder encoder,
            AuthorizationServerSettings settings,
            IamAuthorizationProperties authorizationProperties) {
        this.encoder = encoder;
        this.settings = settings;
        this.authorizationProperties = authorizationProperties;
    }

    public OAuth2AccessToken issue(
            IamAuthorizationContext context,
            String sessionId,
            String clientId,
            Instant issuedAt,
            Instant expiresAt,
            Set<String> scopes) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(settings.getIssuer())
                .subject(context.userId())
                .audience(java.util.List.of(clientId))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId)
                .claim("client_id", clientId)
                .claim("user_type", context.userType().name())
                .claim("roles", new ArrayList<>(context.roles()))
                .claim("permissions", new ArrayList<>(context.permissions()))
                .claim("factory_ids", new ArrayList<>(context.factoryIds()));
        if (context.externalPartyBound()) {
            claims.claim("party_type", context.partyType().name())
                    .claim("party_id", context.partyId());
        }
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256)
                        .keyId(authorizationProperties.getKey().getKeyId())
                        .build(),
                claims.build()));
        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                jwt.getTokenValue(),
                issuedAt,
                expiresAt,
                scopes);
    }
}
