package io.github.chrisshi.mom.iam.domain.type;

/** 安全审计事件操作人类型。 */
public enum SecurityAuditActorType {
    /** 普通用户。 */ USER,
    /** 管理操作。 */ ADMIN,
    /** 系统任务。 */ SYSTEM,
    /** 匿名主体。 */ ANONYMOUS
}
