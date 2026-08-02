package io.github.chrisshi.mom.iam.application.admin.port;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;

import java.util.List;

/** 安全审计只读 Port。 */
public interface IamSecurityAuditQueryPort {
    List<IamAdminViews.SecurityAuditView> listAudit(
            String category, String targetId, int limit, int offset);
}
