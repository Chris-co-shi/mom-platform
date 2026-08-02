package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.application.admin.model.IamSecurityAuditEvent;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditSink;
import io.github.chrisshi.mom.iam.domain.model.IamDomainRules;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamSecurityAuditEventEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamSecurityAuditEventMapper;

/** 安全审计追加 Adapter；兼容既有 Entity 调用并实现 Admin Application Port。 */
public class IamSecurityAuditEventAppender implements IamSecurityAuditSink {
    private final IamSecurityAuditEventMapper mapper;

    public IamSecurityAuditEventAppender(IamSecurityAuditEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(IamSecurityAuditEvent event) {
        IamSecurityAuditEventEntity entity = new IamSecurityAuditEventEntity();
        entity.setId(event.id());
        entity.setEventType(event.eventType());
        entity.setEventCategory(event.eventCategory());
        entity.setRiskLevel(event.riskLevel());
        entity.setResult(event.result());
        entity.setActorType(event.actorType());
        entity.setActorUserId(event.actorUserId());
        entity.setActorClientId(event.actorClientId());
        entity.setTargetType(event.targetType());
        entity.setTargetId(event.targetId());
        entity.setSessionId(event.sessionId());
        entity.setIpAddress(event.ipAddress());
        entity.setUserAgent(event.userAgent());
        entity.setReasonCode(event.reasonCode());
        entity.setReasonDetail(event.reasonDetail());
        entity.setChangeSummary(event.changeSummary());
        entity.setCorrelationId(event.correlationId());
        entity.setOccurredAt(event.occurredAt());
        entity.setCreatedAt(event.occurredAt());
        append(entity);
    }

    /** 既有认证/协议路径继续可直接追加已构造 Entity。 */
    public void append(IamSecurityAuditEventEntity event) {
        IamDomainRules.requireSafeAuditPayload(
                event.getReasonDetail(), event.getChangeSummary());
        if (mapper.append(event) != 1) {
            throw new IllegalStateException("安全审计事件写入失败");
        }
    }
}
