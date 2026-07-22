package io.github.chrisshi.mom.security.authorization;

import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** 业务服务最终 Permission、Factory Scope、Party Scope 与对象可见性授权器。 */
public final class MomAuthorizationService {

    /** 从业务服务已验证的 Bearer JWT 获取当前授权上下文。 */
    public MomJwtAuthorization current(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("缺少已认证 Bearer Token");
        }
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new AccessDeniedException("认证主体不是 MOM JWT");
        }
        try {
            return MomJwtAuthorization.from(jwt);
        }
        catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("MOM JWT 授权上下文无效", exception);
        }
    }

    public boolean hasPermission(Authentication authentication, String permission) {
        return current(authentication).hasPermission(permission);
    }

    public void requirePermission(Authentication authentication, String permission) {
        if (!hasPermission(authentication, permission)) {
            throw new AccessDeniedException("缺少业务 Permission: " + permission);
        }
    }

    /** 当前工厂 Header 只是工作上下文，必须属于 Token 的 factory_ids。 */
    public void requireCurrentFactory(Authentication authentication, String currentFactoryId) {
        MomJwtAuthorization authorization = current(authentication);
        if (currentFactoryId == null || currentFactoryId.isBlank()
                || !authorization.canAccessFactory(currentFactoryId.trim())) {
            throw new AccessDeniedException("当前 Factory 不在授权范围内");
        }
    }

    /**
     * 校验对象归属。任何不匹配统一抛出 404 契约异常，不暴露对象是否真实存在。
     */
    public void requireObjectVisible(
            Authentication authentication,
            String objectFactoryId,
            String objectPartyType,
            String objectPartyId) {
        MomJwtAuthorization authorization = current(authentication);
        if (objectFactoryId == null || !authorization.canAccessFactory(objectFactoryId)) {
            throw new MomScopedResourceNotFoundException();
        }
        if (authorization.externalPartyBound()) {
            if (!authorization.partyType().equals(objectPartyType)
                    || !authorization.partyId().equals(objectPartyId)) {
                throw new MomScopedResourceNotFoundException();
            }
        }
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        return authentication.getPrincipal() instanceof Jwt jwt ? jwt : null;
    }
}
