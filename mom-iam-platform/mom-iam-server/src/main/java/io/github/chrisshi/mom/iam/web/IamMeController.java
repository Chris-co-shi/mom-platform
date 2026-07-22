package io.github.chrisshi.mom.iam.web;

import io.github.chrisshi.mom.iam.security.IamAuthorizationContext;
import io.github.chrisshi.mom.iam.security.IamAuthorizationContextService;
import io.github.chrisshi.mom.iam.security.IamScopeGuard;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/** `/api/iam/me` 处理器，由 IAM Authorization AutoConfiguration 条件注册对应 MVC 路由。 */
public final class IamMeController {
    public static final String CURRENT_FACTORY_HEADER = "X-Factory-Id";

    private final IamAuthorizationContextService contexts;
    private final IamScopeGuard scopeGuard;

    public IamMeController(IamAuthorizationContextService contexts, IamScopeGuard scopeGuard) {
        this.contexts = contexts;
        this.scopeGuard = scopeGuard;
    }

    /**
     * 返回数据库当前有效 Role、Permission、Factory 与 Party Scope。
     *
     * <p>即使 Access Token 中已有 Claims，本接口仍重新读取 IAM 权威数据，以便前端启动时校验当前权限
     * 与 Current Factory 偏好。它不返回密码、Token、Session Cookie 或 Authorization 数据。</p>
     */
    public IamMeResponse me(Authentication authentication, String requestedFactoryId) {
        IamAuthorizationContext context = loadContext(authentication);
        String currentFactoryId = scopeGuard.requireCurrentFactory(context, requestedFactoryId);
        String clientId = authentication.getPrincipal() instanceof Jwt jwt
                ? jwt.getClaimAsString("client_id") : null;
        return IamMeResponse.from(context, clientId, currentFactoryId);
    }

    private IamAuthorizationContext loadContext(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("缺少已认证用户");
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return contexts.loadByUserId(jwt.getSubject());
        }
        return contexts.loadByUsername(authentication.getName());
    }

    /** 对外稳定的当前用户授权响应，不包含任何凭证或数据库内部状态。 */
    public record IamMeResponse(
            String userId,
            String username,
            String displayName,
            String userType,
            String clientId,
            List<String> roles,
            List<String> permissions,
            List<String> factoryIds,
            String partyType,
            String partyId,
            String currentFactoryId) {

        private static IamMeResponse from(
                IamAuthorizationContext context,
                String clientId,
                String currentFactoryId) {
            return new IamMeResponse(
                    context.userId(),
                    context.username(),
                    context.displayName(),
                    context.userType().name(),
                    clientId,
                    context.roles(),
                    context.permissions(),
                    context.factoryIds(),
                    context.partyType() == null ? null : context.partyType().name(),
                    context.partyId(),
                    currentFactoryId);
        }
    }
}
