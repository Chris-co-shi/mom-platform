package io.github.chrisshi.mom.security.actor;

import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.AuditActorMissingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spring Security 当前操作人映射测试。 */
class SecurityCurrentActorProviderTest {

    private final SecurityCurrentActorProvider provider = new SecurityCurrentActorProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedUserShouldUseAuthenticationName() {
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("user-100", "n/a", "ROLE_USER")
        );

        AuditActor actor = provider.requireCurrentActor();

        assertEquals("user-100", actor.actorId());
        assertEquals(ActorType.USER, actor.actorType());
    }

    @Test
    void unauthenticatedAuthenticationShouldBeIgnored() {
        TestingAuthenticationToken authentication =
            new TestingAuthenticationToken("user-101", "n/a");
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertTrue(provider.findCurrentActor().isEmpty());
        assertThrows(AuditActorMissingException.class, provider::requireCurrentActor);
    }

    @Test
    void anonymousAuthenticationShouldBeIgnored() {
        SecurityContextHolder.getContext().setAuthentication(
            new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
            )
        );

        assertTrue(provider.findCurrentActor().isEmpty());
        assertThrows(AuditActorMissingException.class, provider::requireCurrentActor);
    }
}
