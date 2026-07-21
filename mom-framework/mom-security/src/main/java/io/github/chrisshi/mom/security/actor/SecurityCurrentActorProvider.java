package io.github.chrisshi.mom.security.actor;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.AuditContext;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * 从显式审计上下文或 Spring Security 上下文解析当前操作人。
 *
 * <p>显式 {@link AuditContext} 优先，确保定时任务、MQ Consumer 和测试可以建立 SYSTEM/ADMIN Actor。
 * 没有显式 Actor 时，只接受已认证且非匿名的 Spring Security 身份。P1.5 S01 不根据 INTERNAL、任意角色或
 * Authority 自动升级为 ADMIN，也不实现 Token 签发、RBAC 或 Scope。</p>
 */
public final class SecurityCurrentActorProvider implements CurrentActorProvider {

    /** 按“显式 Actor → 已认证用户 → 空”的顺序解析。 */
    @Override
    public Optional<AuditActor> findCurrentActor() {
        Optional<AuditActor> explicitActor = AuditContext.findCurrentActor();
        if (explicitActor.isPresent()) {
            return explicitActor;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        Jwt jwt = extractJwt(authentication);
        String actorId = jwt == null ? normalize(authentication.getName()) : normalize(jwt.getSubject());
        if (actorId == null) {
            return Optional.empty();
        }

        return Optional.of(new AuditActor(
                actorId,
                ActorType.USER,
                claim(jwt, "user_type"),
                claim(jwt, "client_id"),
                claim(jwt, "sid"),
                CorrelationContext.currentId()));
    }

    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    private static String claim(Jwt jwt, String claimName) {
        if (jwt == null) {
            return null;
        }
        Object value = jwt.getClaims().get(claimName);
        return value == null ? null : normalize(String.valueOf(value));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
