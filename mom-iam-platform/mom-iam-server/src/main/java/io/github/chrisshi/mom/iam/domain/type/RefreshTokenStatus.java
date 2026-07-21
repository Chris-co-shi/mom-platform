package io.github.chrisshi.mom.iam.domain.type;

/** Refresh Token 轮换链状态。 */
public enum RefreshTokenStatus {
    /** 当前有效。 */ ACTIVE,
    /** 已轮换。 */ ROTATED,
    /** 已撤销。 */ REVOKED,
    /** 已过期。 */ EXPIRED
}
