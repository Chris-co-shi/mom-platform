package io.github.chrisshi.mom.security.token;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;
import java.util.Set;

/**
 * 从已验证 Access Token 提取的不可变业务授权上下文。
 *
 * <p>该对象只承载 JWT 快照。普通 Role、Permission 与 Factory Scope 变更按设计在下一次 Refresh 后生效；
 * 高风险变更必须撤销 Session，使 Gateway 的 revoked sid 检查立即 Fail Closed。</p>
 */
public record MomJwtAuthorization(
        String userId,
        String sessionId,
        String clientId,
        String userType,
        Set<String> roles,
        Set<String> permissions,
        Set<String> factoryIds,
        String partyType,
        String partyId) {

    public MomJwtAuthorization {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(userType, "userType");
        roles = Set.copyOf(roles == null ? Set.of() : roles);
        permissions = Set.copyOf(permissions == null ? Set.of() : permissions);
        factoryIds = Set.copyOf(factoryIds == null ? Set.of() : factoryIds);
    }

    /** 从完成验证的 JWT 建立业务授权上下文。 */
    public static MomJwtAuthorization from(Jwt jwt) {
        return new MomJwtAuthorization(
                required(jwt.getSubject(), "sub"),
                required(MomSecurityClaims.stringClaim(jwt, MomSecurityClaims.SESSION_ID), "sid"),
                required(MomSecurityClaims.stringClaim(jwt, MomSecurityClaims.CLIENT_ID), "client_id"),
                required(MomSecurityClaims.stringClaim(jwt, MomSecurityClaims.USER_TYPE), "user_type"),
                MomSecurityClaims.stringSetClaim(jwt, MomSecurityClaims.ROLES),
                MomSecurityClaims.stringSetClaim(jwt, MomSecurityClaims.PERMISSIONS),
                MomSecurityClaims.stringSetClaim(jwt, MomSecurityClaims.FACTORY_IDS),
                MomSecurityClaims.stringClaim(jwt, MomSecurityClaims.PARTY_TYPE),
                MomSecurityClaims.stringClaim(jwt, MomSecurityClaims.PARTY_ID));
    }

    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission);
    }

    public boolean canAccessFactory(String factoryId) {
        return factoryId != null && factoryIds.contains(factoryId);
    }

    public boolean externalPartyBound() {
        return partyType != null && partyId != null;
    }

    private static String required(String value, String claimName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JWT 缺少必需 Claim: " + claimName);
        }
        return value;
    }
}
