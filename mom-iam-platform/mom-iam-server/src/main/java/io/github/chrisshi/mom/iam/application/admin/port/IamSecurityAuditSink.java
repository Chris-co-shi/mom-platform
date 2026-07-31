package io.github.chrisshi.mom.iam.application.admin.port;

import io.github.chrisshi.mom.iam.application.admin.model.IamSecurityAuditEvent;

/** 追加型安全审计 Outbound Port。 */
public interface IamSecurityAuditSink {
    void append(IamSecurityAuditEvent event);
}
