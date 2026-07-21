package io.github.chrisshi.mom.data.audit;

import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.AuditActorMissingException;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.data.config.MomDataAuditProperties;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 审计字段强制覆盖、缺失 Actor 失败和禁止填充领域字段测试。 */
class MomMetaObjectHandlerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-07-20T01:02:03Z");

    @Test
    void insertShouldForceSameTimeAndActorOverCallerValues() {
        AuditTestEntity entity = new AuditTestEntity();
        entity.setCreatedAt(Instant.EPOCH);
        entity.setUpdatedAt(Instant.EPOCH);
        entity.setCreatedBy("forged-created");
        entity.setUpdatedBy("forged-updated");
        MomMetaObjectHandler handler = handler(actorProvider("user-1"), true);

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertEquals(FIXED_TIME, entity.getCreatedAt());
        assertEquals(FIXED_TIME, entity.getUpdatedAt());
        assertEquals("user-1", entity.getCreatedBy());
        assertEquals("user-1", entity.getUpdatedBy());
    }

    @Test
    void updateShouldOnlyChangeUpdatedAuditFields() {
        AuditTestEntity entity = new AuditTestEntity();
        Instant createdAt = Instant.parse("2026-07-19T00:00:00Z");
        entity.setCreatedAt(createdAt);
        entity.setCreatedBy("creator");
        entity.setUpdatedAt(Instant.EPOCH);
        entity.setUpdatedBy("forged");

        handler(actorProvider("user-2"), true).updateFill(SystemMetaObject.forObject(entity));

        assertEquals(createdAt, entity.getCreatedAt());
        assertEquals("creator", entity.getCreatedBy());
        assertEquals(FIXED_TIME, entity.getUpdatedAt());
        assertEquals("user-2", entity.getUpdatedBy());
    }

    @Test
    void missingActorShouldFailClosed() {
        CurrentActorProvider missing = Optional::empty;
        assertThrows(AuditActorMissingException.class, () ->
                handler(missing, true).insertFill(SystemMetaObject.forObject(new AuditTestEntity())));
    }

    @Test
    void relaxedExceptionalModeShouldClearForgedActorInsteadOfTrustingCaller() {
        AuditTestEntity entity = new AuditTestEntity();
        entity.setCreatedBy("forged");
        entity.setUpdatedBy("forged");
        handler(Optional::empty, false).insertFill(SystemMetaObject.forObject(entity));
        assertNull(entity.getCreatedBy());
        assertNull(entity.getUpdatedBy());
    }

    @Test
    void forbiddenDomainFieldsMustRemainUntouched() {
        AuditTestEntity entity = new AuditTestEntity();
        entity.setFactoryId("factory-forged");
        entity.setPartyId("party-forged");
        entity.setSessionId("session-forged");
        handler(actorProvider("user-3"), true).insertFill(SystemMetaObject.forObject(entity));
        assertEquals("factory-forged", entity.getFactoryId());
        assertEquals("party-forged", entity.getPartyId());
        assertEquals("session-forged", entity.getSessionId());
    }

    private static MomMetaObjectHandler handler(CurrentActorProvider provider, boolean failOnMissing) {
        MomDataAuditProperties properties = new MomDataAuditProperties();
        properties.setFailOnMissingActor(failOnMissing);
        return new MomMetaObjectHandler(Clock.fixed(FIXED_TIME, ZoneOffset.UTC), provider, properties);
    }

    private static CurrentActorProvider actorProvider(String actorId) {
        return () -> Optional.of(new AuditActor(
                actorId, ActorType.USER, "INTERNAL", "mom-admin-web", "sid-1", "corr-1"));
    }

    private static final class AuditTestEntity extends BaseEntity {
        private String factoryId;
        private String partyId;
        private String sessionId;
        public String getFactoryId() { return factoryId; }
        public void setFactoryId(String factoryId) { this.factoryId = factoryId; }
        public String getPartyId() { return partyId; }
        public void setPartyId(String partyId) { this.partyId = partyId; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }
}
