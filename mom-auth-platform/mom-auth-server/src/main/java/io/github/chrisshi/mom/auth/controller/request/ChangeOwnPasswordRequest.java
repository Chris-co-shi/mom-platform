package io.github.chrisshi.mom.auth.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Auth 个人改密的不可变 HTTP 入参，与管理员重置分离。
 * 只依赖校验 API，不访问基础设施；凭据限本次调用，禁止日志和响应回显。
 * @param currentPassword 原密码，保持原始字符
 * @param newPassword 新密码，沿用 8 到 128 字符规则
 * @param version 当前资料的乐观锁版本
 */
public record ChangeOwnPasswordRequest(
    @NotBlank @Size(max = 128) String currentPassword,
    @NotBlank @Size(min = 8, max = 128) String newPassword,
    @NotNull @PositiveOrZero Long version
) {
    /** 隐去凭据，避免默认记录类型 toString 泄露密码；无副作用。 */
    @Override
    public String toString() {
        return "ChangeOwnPasswordRequest[credentials=REDACTED]";
    }
}
