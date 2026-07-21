package io.github.chrisshi.mom.iam.domain.type;

/** Permission 与安全事件的稳定风险等级。 */
public enum PermissionRiskLevel {
    /** 低风险只读或普通操作。 */ LOW,
    /** 中风险配置操作。 */ MEDIUM,
    /** 高风险身份或安全操作。 */ HIGH
}
