package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditActorType;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditResult;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 追加型 IAM 安全审计事件；无普通更新、逻辑删除或版本字段。 */
@Getter
@Setter
@TableName("iam_security_audit_event")
public class IamSecurityAuditEventEntity extends BaseIdEntity {
    @TableField("event_type") private String eventType;
    @TableField("event_category") private SecurityEventCategory eventCategory;
    @TableField("risk_level") private PermissionRiskLevel riskLevel;
    @TableField("result") private SecurityAuditResult result;
    @TableField("actor_type") private SecurityAuditActorType actorType;
    @TableField("actor_user_id") private String actorUserId;
    @TableField("actor_client_id") private String actorClientId;
    @TableField("target_type") private String targetType;
    @TableField("target_id") private String targetId;
    @TableField("session_id") private String sessionId;
    @TableField("ip_address") private String ipAddress;
    @TableField("user_agent") private String userAgent;
    @TableField("reason_code") private String reasonCode;
    @TableField("reason_detail") private String reasonDetail;
    @TableField("change_summary") private String changeSummary;
    @TableField("correlation_id") private String correlationId;
    @TableField("occurred_at") private Instant occurredAt;
    @TableField("created_at") private Instant createdAt;
}
