package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamSecurityAuditEventEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** IAM 追加型安全审计 Mapper；应用层只暴露 append。 */
@Mapper
public interface IamSecurityAuditEventMapper extends MomBaseMapper<IamSecurityAuditEventEntity> {

    /**
     * 以 PostgreSQL JSONB 显式写入安全审计事件。
     *
     * @param event 已完成敏感信息校验的事件
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO iam_security_audit_event (
                id, event_type, event_category, risk_level, result, actor_type,
                actor_user_id, actor_client_id, target_type, target_id, session_id,
                ip_address, user_agent, reason_code, reason_detail, change_summary,
                correlation_id, occurred_at, created_at
            ) VALUES (
                #{id}, #{eventType}, #{eventCategory}, #{riskLevel}, #{result}, #{actorType},
                #{actorUserId}, #{actorClientId}, #{targetType}, #{targetId}, #{sessionId},
                #{ipAddress}, #{userAgent}, #{reasonCode}, #{reasonDetail},
                CAST(#{changeSummary} AS jsonb), #{correlationId}, #{occurredAt}, #{createdAt}
            )
            """)
    int append(IamSecurityAuditEventEntity event);

    /**
     * 查询追加型安全审计投影。
     *
     * @return 不包含 IP、User-Agent 或凭证材料的稳定分页结果
     */
    List<IamAdminViews.SecurityAuditView> selectAdminAudit(
            @Param("category") String category, @Param("targetId") String targetId,
            @Param("limit") int limit, @Param("offset") int offset);
}
