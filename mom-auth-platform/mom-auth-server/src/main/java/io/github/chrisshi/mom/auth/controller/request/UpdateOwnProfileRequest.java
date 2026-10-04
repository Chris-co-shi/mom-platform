package io.github.chrisshi.mom.auth.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Auth 自助资料修改的不可变 HTTP 入参，仅开放显示名和版本。
 * 不接收目标用户或管理字段，不依赖持久化；校验失败拒绝，无基础设施副作用。
 * @param displayName 必填显示名，最多 200 字符
 * @param version 读取资料时取得的非负版本
 */
public record UpdateOwnProfileRequest(
    @NotBlank @Size(max = 200) String displayName,
    @NotNull @PositiveOrZero Long version
) { }
