package io.github.chrisshi.mom.iam.security;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;

import java.util.ArrayList;

/**
 * 从 IAM 权威授权快照组装 PC JSON 与 OAuth2/OIDC 共用的 JWT Claims。
 *
 * <p>该组件不访问数据库、不签名、不创建 Session，也不依赖 HTTP 协议对象。调用方必须提供已经由
 * IAM 加载并校验的不可变授权上下文；客户端提交的 Role、Permission、Factory 或 Party 不得进入
 * 本组件。组件本身无可变状态，可被并发复用。</p>
 */
public final class IamJwtClaimsAssembler {

    /**
     * 写入所有 Token 共用的身份 Claims。
     *
     * @param claims JWT Claims 构建器
     * @param authorization IAM 权威授权快照
     * @param clientId 已校验的 OAuth Client ID
     */
    public void applyIdentity(
            JwtClaimsSet.Builder claims,
            IamAuthorizationContext authorization,
            String clientId) {
        claims.subject(authorization.userId())
                .claim("client_id", clientId)
                .claim("user_type", authorization.userType().name());
    }

    /**
     * 写入 Access Token 的 Session 与授权 Claims。
     *
     * @param claims JWT Claims 构建器
     * @param authorization IAM 权威授权快照
     * @param sessionId 已由权威 Session 服务创建或旋转的 sid
     */
    public void applyAccessAuthorization(
            JwtClaimsSet.Builder claims,
            IamAuthorizationContext authorization,
            String sessionId) {
        if (sessionId != null) {
            claims.claim("sid", sessionId);
        }
        claims.claim("roles", new ArrayList<>(authorization.roles()))
                .claim("permissions", new ArrayList<>(authorization.permissions()))
                .claim("factory_ids", new ArrayList<>(authorization.factoryIds()));
        if (authorization.externalPartyBound()) {
            claims.claim("party_type", authorization.partyType().name())
                    .claim("party_id", authorization.partyId());
        }
    }

    /**
     * 写入 OIDC ID Token 当前已发布的展示 Claims。
     *
     * @param claims JWT Claims 构建器
     * @param authorization IAM 权威授权快照
     */
    public void applyOidcIdentity(
            JwtClaimsSet.Builder claims,
            IamAuthorizationContext authorization) {
        claims.claim("preferred_username", authorization.username())
                .claim("name", authorization.displayName());
    }
}
