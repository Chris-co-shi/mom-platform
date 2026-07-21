package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.domain.model.IamDomainRules;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamSecurityAuditEventEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamSecurityAuditEventMapper;

/** 安全审计追加端口；只暴露 append，不暴露普通 update/delete。 */
public class IamSecurityAuditEventAppender {
    private final IamSecurityAuditEventMapper mapper;

    /** @param mapper 安全审计 Mapper */
    public IamSecurityAuditEventAppender(IamSecurityAuditEventMapper mapper) { this.mapper = mapper; }

    /** @param event 已完成结构准备的安全事件 */
    public void append(IamSecurityAuditEventEntity event) {
        IamDomainRules.requireSafeAuditPayload(event.getReasonDetail(), event.getChangeSummary());
        if (mapper.append(event) != 1) throw new IllegalStateException("安全审计事件写入失败");
    }
}
