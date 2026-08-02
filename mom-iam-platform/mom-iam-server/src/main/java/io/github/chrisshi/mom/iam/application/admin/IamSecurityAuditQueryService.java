package io.github.chrisshi.mom.iam.application.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminActor;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditQueryPort;

import java.util.List;

/** IAM 追加型安全审计只读用例。 */
public final class IamSecurityAuditQueryService {
    private final IamSecurityAuditQueryPort queries;

    public IamSecurityAuditQueryService(IamSecurityAuditQueryPort queries) {
        this.queries = queries;
    }

    public List<IamAdminViews.SecurityAuditView> listAudit(
            IamAdminActor actor, String category, String targetId, int limit, int offset) {
        actor.requirePermission("iam:audit:read");
        return queries.listAudit(
                category, targetId,
                IamAdminCommandValidator.pageSize(limit),
                IamAdminCommandValidator.pageOffset(offset));
    }
}
