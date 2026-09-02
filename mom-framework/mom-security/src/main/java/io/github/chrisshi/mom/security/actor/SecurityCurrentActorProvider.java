package io.github.chrisshi.mom.security.actor;

import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 从 Spring Security 上下文解析当前操作人。
 *
 * <p>只接受已认证且非匿名的身份。操作人 ID 使用 {@link Authentication#getName()}，因此不依赖
 * JWT、Opaque Token 或其他具体认证协议。</p>
 */
public final class SecurityCurrentActorProvider implements CurrentActorProvider {

    @Override
    public Optional<AuditActor> findCurrentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        String actorId = authentication.getName();
        if (actorId == null || actorId.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new AuditActor(actorId, ActorType.USER));
    }
}
