package io.github.chrisshi.mom.iam.application.admin;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminActor;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminRequestContext;
import io.github.chrisshi.mom.iam.application.admin.model.IamSecurityAuditEvent;
import io.github.chrisshi.mom.iam.application.admin.port.IamIdentifierGenerator;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditSink;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditActorType;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditResult;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;

import java.time.Clock;
import java.time.Instant;

/** 构造并追加 IAM Admin 成功审计事件。 */
public final class IamAdminAuditService {
    private final IamSecurityAuditSink sink;
    private final IamIdentifierGenerator ids;
    private final Clock clock;

    public IamAdminAuditService(
            IamSecurityAuditSink sink, IamIdentifierGenerator ids, Clock clock) {
        this.sink = sink;
        this.ids = ids;
        this.clock = clock;
    }

    public void record(
            IamAdminActor actor,
            IamAdminRequestContext request,
            String eventType,
            SecurityEventCategory category,
            PermissionRiskLevel risk,
            String targetType,
            String targetId,
            String sessionId,
            String reasonCode,
            String reasonDetail,
            String changeSummary) {
        Instant now = clock.instant();
        sink.append(new IamSecurityAuditEvent(
                ids.nextId(),
                eventType,
                category,
                risk,
                SecurityAuditResult.SUCCESS,
                SecurityAuditActorType.ADMIN,
                actor.userId(),
                actor.clientId(),
                targetType,
                targetId,
                sessionId,
                IamAdminCommandValidator.trim(request == null ? null : request.ipAddress(), 64),
                IamAdminCommandValidator.trim(request == null ? null : request.userAgent(), 1000),
                IamAdminCommandValidator.trim(reasonCode, 100),
                IamAdminCommandValidator.trim(reasonDetail, 2000),
                changeSummary == null ? "{}" : changeSummary,
                IamAdminCommandValidator.trim(CorrelationContext.currentId(), 128),
                now));
    }
}
