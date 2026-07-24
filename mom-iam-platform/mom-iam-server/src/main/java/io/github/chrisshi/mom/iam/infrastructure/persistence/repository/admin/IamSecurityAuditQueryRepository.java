package io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamSecurityAuditEventMapper;

import java.util.List;

/** 追加型 IAM 安全审计的只读管理仓储；写入仍由专用 Appender 负责。 */
public final class IamSecurityAuditQueryRepository {
    private final IamSecurityAuditEventMapper mapper;

    /** @param mapper 安全审计事件 Mapper */
    public IamSecurityAuditQueryRepository(IamSecurityAuditEventMapper mapper) {
        this.mapper = mapper;
    }

    /** @return 不含 IP、User-Agent 或凭证材料的安全审计分页结果 */
    public List<IamAdminViews.SecurityAuditView> listAudit(
            String category, String targetId, int limit, int offset) {
        return mapper.selectAdminAudit(category, targetId, limit, offset);
    }
}
