package io.github.chrisshi.mom.iam.infrastructure.persistence.query;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditQueryPort;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamSecurityAuditEventMapper;

import java.util.List;

/** IAM 安全审计只读 Adapter。 */
public final class MybatisIamSecurityAuditQuery implements IamSecurityAuditQueryPort {
    private final IamSecurityAuditEventMapper mapper;

    public MybatisIamSecurityAuditQuery(IamSecurityAuditEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<IamAdminViews.SecurityAuditView> listAudit(
            String category, String targetId, int limit, int offset) {
        return mapper.selectAdminAudit(category, targetId, limit, offset);
    }
}
