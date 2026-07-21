package io.github.chrisshi.mom.iam.domain.type;

/** 用户授权 Session 状态。 */
public enum UserSessionStatus {
    /** 有效。 */ ACTIVE,
    /** 已撤销。 */ REVOKED,
    /** 已确认泄漏。 */ COMPROMISED,
    /** 已过期。 */ EXPIRED
}
