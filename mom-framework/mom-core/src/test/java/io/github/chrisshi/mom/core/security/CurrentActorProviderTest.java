package io.github.chrisshi.mom.core.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CurrentActorProvider 缺失 Actor 时的 fail-closed 契约测试。 */
class CurrentActorProviderTest {

    @Test
    void requireCurrentActorShouldReturnExistingActor() {
        AuditActor actor = new AuditActor("user-1", ActorType.USER, null, null, null, null);
        CurrentActorProvider provider = () -> Optional.of(actor);

        assertEquals(actor, provider.requireCurrentActor());
    }

    @Test
    void requireCurrentActorShouldFailWithoutDefaultSystemFallback() {
        CurrentActorProvider provider = Optional::empty;

        assertThrows(AuditActorMissingException.class, provider::requireCurrentActor);
    }
}
