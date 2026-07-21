package io.github.chrisshi.mom.security.actor;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.AuditActorMissingException;
import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spring Security Claim 映射、匿名排除和显式 Actor 优先级测试。 */
class SecurityCurrentActorProviderTest {

    private final SecurityCurrentActorProvider provider = new SecurityCurrentActorProvider();
    private final AuditContextExecutor executor = new AuditContextExecutor();

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        CorrelationContext.clear();
    }

    @Test
    void authenticatedJwtShouldMapFrozenClaims() {
        CorrelationContext.set("corr-1");
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("user-100")
                .issuedAt(Instant.parse("2026-07-20T00:00:00Z"))
                .expiresAt(Instant.parse("2026-07-20T00:10:00Z"))
                .claim("user_type", "INTERNAL")
                .claim("client_id", "mom-admin-web")
                .claim("sid", "session-1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                jwt.getSubject()));

        AuditActor actor = provider.requireCurrentActor();
        assertEquals("user-100", actor.actorId());
        assertEquals(ActorType.USER, actor.actorType());
        assertEquals("INTERNAL", actor.userType());
        assertEquals("mom-admin-web", actor.clientId());
        assertEquals("session-1", actor.sessionId());
        assertEquals("corr-1", actor.correlationId());
    }

    @Test
    void optionalClaimsMayBeAbsent() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("user-101")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                jwt.getSubject()));

        AuditActor actor = provider.requireCurrentActor();
        assertEquals("user-101", actor.actorId());
        assertNull(actor.userType());
        assertNull(actor.clientId());
        assertNull(actor.sessionId());
    }

    @Test
    void genericAuthenticatedUserShouldUseStableAuthenticationName() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user-102", "n/a", "ROLE_USER"));
        AuditActor actor = provider.requireCurrentActor();
        assertEquals("user-102", actor.actorId());
        assertEquals(ActorType.USER, actor.actorType());
    }

    @Test
    void unauthenticatedAndAnonymousAuthenticationShouldBeIgnored() {
        TestingAuthenticationToken unauthenticated = new TestingAuthenticationToken("user", "n/a");
        unauthenticated.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        assertTrue(provider.findCurrentActor().isEmpty());

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertTrue(provider.findCurrentActor().isEmpty());
        assertThrows(AuditActorMissingException.class, provider::requireCurrentActor);
    }

    @Test
    void explicitSystemActorShouldOverrideSecurityUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user-103", "n/a", "ROLE_USER"));
        AuditActor actor = executor.runAsSystem("mom-qms-message-consumer", provider::requireCurrentActor);
        assertEquals("mom-qms-message-consumer", actor.actorId());
        assertEquals(ActorType.SYSTEM, actor.actorType());
        assertEquals("user-103", provider.requireCurrentActor().actorId());
    }
}
