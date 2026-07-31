package io.github.chrisshi.mom.iam.application.admin.model;

import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditActorType;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditResult;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;

import java.time.Instant;

/** Application 向安全审计 Outbound Port 提交的框架无关事件。 */
public record IamSecurityAuditEvent(
        String id,
        String eventType,
        SecurityEventCategory eventCategory,
        PermissionRiskLevel riskLevel,
        SecurityAuditResult result,
        SecurityAuditActorType actorType,
        String actorUserId,
        String actorClientId,
        String targetType,
        String targetId,
        String sessionId,
        String ipAddress,
        String userAgent,
        String reasonCode,
        String reasonDetail,
        String changeSummary,
        String correlationId,
        Instant occurredAt) { }
