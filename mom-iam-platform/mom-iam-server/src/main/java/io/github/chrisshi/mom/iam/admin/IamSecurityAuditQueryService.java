package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.infrastructure.persistence.query.IamSecurityAuditQueryRepository;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * IAM 追加型安全审计的只读应用查询服务。
 *
 * <p>该服务仅执行 Permission、分页规范化与非敏感投影查询，不修改审计事件、不开启
 * 事务，不依赖 Web DTO 或 Mapper。持久化查询失败向上传播，不返回伪结果。</p>
 */
final class IamSecurityAuditQueryService {
    private final IamSecurityAuditQueryRepository auditQueries;
    private final IamAdminOperationSupport support;

    IamSecurityAuditQueryService(
            IamSecurityAuditQueryRepository auditQueries,
            IamAdminOperationSupport support) {
        this.auditQueries = auditQueries;
        this.support = support;
    }

    List<IamAdminViews.SecurityAuditView> listAudit(
            Authentication authentication, String category, String targetId, int limit, int offset) {
        support.requirePermission(authentication, "iam:audit:read");
        return auditQueries.listAudit(category, targetId,
                IamAdminCommandValidator.pageSize(limit), IamAdminCommandValidator.pageOffset(offset));
    }
}
