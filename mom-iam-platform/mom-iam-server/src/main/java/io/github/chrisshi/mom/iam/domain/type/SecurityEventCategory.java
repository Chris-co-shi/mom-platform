package io.github.chrisshi.mom.iam.domain.type;

/** IAM 安全事件稳定分类。 */
public enum SecurityEventCategory {
    /** 认证。 */ AUTHENTICATION,
    /** 账号。 */ ACCOUNT,
    /** 授权。 */ AUTHORIZATION,
    /** 会话。 */ SESSION,
    /** 令牌。 */ TOKEN,
    /** 客户端。 */ CLIENT,
    /** 综合安全。 */ SECURITY
}
