package io.github.chrisshi.mom.auth.application;

import io.github.chrisshi.mom.core.error.ErrorCode;

public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS("auth.invalid_credentials", "auth.error.invalid-credentials", "用户名或密码错误"),
    ACCOUNT_DISABLED("auth.account_disabled", "auth.error.account-disabled", "账号已停用"),
    RESOURCE_NOT_FOUND("auth.resource_not_found", "auth.error.resource-not-found", "资源不存在"),
    USERNAME_CONFLICT("auth.username_conflict", "auth.error.username-conflict", "用户名已存在"),
    ROLE_CODE_CONFLICT("auth.role_code_conflict", "auth.error.role-code-conflict", "角色编码已存在"),
    PERMISSION_CODE_CONFLICT("auth.permission_code_conflict", "auth.error.permission-code-conflict", "权限编码已存在"),
    RESOURCE_REFERENCED("auth.resource_referenced", "auth.error.resource-referenced", "资源仍被引用，不能删除"),
    OPTIMISTIC_LOCK_CONFLICT("auth.optimistic_lock_conflict", "auth.error.optimistic-lock-conflict", "数据已被其他操作修改，请刷新后重试"),
    TOKEN_STORE_UNAVAILABLE("auth.token_store_unavailable", "auth.error.token-store-unavailable", "认证令牌服务暂时不可用");

    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    AuthErrorCode(String code, String messageKey, String defaultMessage) {
        this.code = code;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
