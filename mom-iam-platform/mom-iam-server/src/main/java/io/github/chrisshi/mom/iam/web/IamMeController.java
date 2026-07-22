package io.github.chrisshi.mom.iam.web;

import io.github.chrisshi.mom.iam.security.IamAuthorizationContext;
import io.github.chrisshi.mom.iam.security.IamAuthorizationContextService;
import io.github.chrisshi.mom.iam.security.IamClientAccessPolicyService;
import io.github.chrisshi.mom.iam.security.IamScopeGuard;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/** `/api/iam/me` 处理器，由 IAM Authorization AutoConfiguration 条件注册对应 MVC 路由。 */
public final class IamMeController {
    public static final String CURRENT_FACTORY_HEADER = "X-Factory-Id";

    private final IamAuthorizationContextService contexts;
    private final IamClientAccessPolicyService clientAccess;
    private final IamScopeGuard scopeGuard;

    /**
     * 创建当前授权上下文控制器。
     *
     * @param contexts Role、Permission、Factory 与 Party 权威上下文服务
     * @param clientAccess Client、用户类型与 Mobile Access 实时入口策略
     * @param scopeGuard 当前 Factory 校验器
     */
    public IamMeController(
            IamAuthorizationContextService contexts,
            IamClientAccessPolicyService clientAccess,
            IamScopeGuard scopeGuard) {
        this.contexts = contexts;
        this.clientAccess = clientAccess;
        this.scopeGuard = scopeGuard;
    }

    /**
     * 返回数据库当前有效 Role、Permission、Factory 与 Party Scope。
     *
     * <p>即使 Access Token 中已有 Claims，本接口仍重新读取 IAM 权威数据，以便前端启动时校验当前权限
     * 与 Current Factory 偏好。Mobile Client 还会重新执行用户类型与 Mobile Access 入口策略；权限已撤销时
     * 直接拒绝，不使用 JWT 中的旧值降级。它不返回密码、Token、Session Cookie 或 Authorization 数据。</p>
     *
     * @param authentication 已由 Resource Server 验证签名、Issuer、Audience 与撤销状态的认证主体
     * @param requestedFactoryId 客户端请求的当前 Factory，可为空
     * @return 数据库当前授权、JWT Session 标识和当前 Client 的 Mobile Access 结果
     * @throws AccessDeniedException Mobile 入口策略已失效时抛出
     */
    public IamMeResponse me(Authentication authentication, String requestedFactoryId) {
        IamAuthorizationContext context = loadContext(authentication);
        String currentFactoryId = scopeGuard.requireCurrentFactory(context, requestedFactoryId);
        String clientId = authentication.getPrincipal() instanceof Jwt jwt
                ? jwt.getClaimAsString("client_id") : null;
        String sessionId = authentication.getPrincipal() instanceof Jwt jwt
                ? jwt.getClaimAsString("sid") : null;
        boolean mobileAccessEnabled = false;
        if ("mom-mobile-pda".equals(clientId)) {
            try {
                clientAccess.requireAuthorization(context.username(), clientId);
            }
            catch (IamClientAccessPolicyService.AccessDeniedException exception) {
                throw new AccessDeniedException("Mobile Access 已失效", exception);
            }
            mobileAccessEnabled = true;
        }
        return IamMeResponse.from(
                context, clientId, sessionId, mobileAccessEnabled, currentFactoryId);
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
            String sid,
            boolean mobileAccessEnabled,
            List<String> roles,
            List<String> permissions,
            List<String> factoryIds,
            String partyType,
            String partyId,
            String currentFactoryId) {

        private static IamMeResponse from(
                IamAuthorizationContext context,
                String clientId,
                String sid,
                boolean mobileAccessEnabled,
                String currentFactoryId) {
            return new IamMeResponse(
                    context.userId(),
                    context.username(),
                    context.displayName(),
                    context.userType().name(),
                    clientId,
                    sid,
                    mobileAccessEnabled,
                    context.roles(),
                    context.permissions(),
                    context.factoryIds(),
                    context.partyType() == null ? null : context.partyType().name(),
                    context.partyId(),
                    currentFactoryId);
        }
    }
}
