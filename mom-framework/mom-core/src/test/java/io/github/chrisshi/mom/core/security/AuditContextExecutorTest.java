package io.github.chrisshi.mom.core.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 审计上下文建立、嵌套恢复、异常清理和线程复用隔离测试。 */
class AuditContextExecutorTest {

    private final AuditContextExecutor executor = new AuditContextExecutor();

    @AfterEach
    void clearContext() {
        AuditContext.clear();
    }

    @Test
    void runAsSystemShouldExposeSystemActorAndClearAfterCompletion() {
        AuditActor actor = executor.runAsSystem(
                "mom-iam-session-cleaner",
                () -> AuditContext.findCurrentActor().orElseThrow());

        assertEquals("mom-iam-session-cleaner", actor.actorId());
        assertEquals(ActorType.SYSTEM, actor.actorType());
        assertTrue(AuditContext.findCurrentActor().isEmpty());
    }

    @Test
    void runAsActorShouldExposeProvidedActorAndClearAfterCompletion() {
        AuditActor expected = actor("user-1", ActorType.USER);
        AuditActor actual = executor.runAsActor(
                expected,
                () -> AuditContext.findCurrentActor().orElseThrow());

        assertEquals(expected, actual);
        assertTrue(AuditContext.findCurrentActor().isEmpty());
    }

    @Test
    void exceptionalCompletionShouldStillClearContext() {
        assertThrows(IllegalStateException.class, () -> executor.runAsSystem(
                "mom-qms-message-consumer",
                () -> {
                    throw new IllegalStateException("expected");
                }));

        assertTrue(AuditContext.findCurrentActor().isEmpty());
    }

    @Test
    void nestedExecutionShouldRestoreOuterActor() {
        AuditActor outer = actor("user-outer", ActorType.USER);
        AuditActor inner = actor("admin-inner", ActorType.ADMIN);

        executor.runAsActor(outer, () -> {
            assertEquals(outer, AuditContext.findCurrentActor().orElseThrow());
            executor.runAsActor(inner, () ->
                    assertEquals(inner, AuditContext.findCurrentActor().orElseThrow()));
            assertEquals(outer, AuditContext.findCurrentActor().orElseThrow());
        });

        assertTrue(AuditContext.findCurrentActor().isEmpty());
    }

    @Test
    void reusedThreadShouldNotSeePreviousActor() throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            String first = pool.submit(() -> executor.runAsSystem(
                    "mom-wms-outbox-publisher",
                    () -> AuditContext.findCurrentActor().orElseThrow().actorId())).get();
            Optional<AuditActor> second = pool.submit(AuditContext::findCurrentActor).get();

            assertEquals("mom-wms-outbox-publisher", first);
            assertFalse(second.isPresent());
        }
    }

    @Test
    void systemActorCodeMustBeStableAndMeaningful() {
        assertThrows(IllegalArgumentException.class, () -> executor.runAsSystem(" ", () -> null));
        assertThrows(IllegalArgumentException.class, () -> executor.runAsSystem("SYSTEM", () -> null));
        assertThrows(IllegalArgumentException.class, () -> executor.runAsSystem("system", () -> null));
    }

    private static AuditActor actor(String id, ActorType type) {
        return new AuditActor(id, type, "INTERNAL", "mom-admin-web", "sid-1", "corr-1");
    }
}
