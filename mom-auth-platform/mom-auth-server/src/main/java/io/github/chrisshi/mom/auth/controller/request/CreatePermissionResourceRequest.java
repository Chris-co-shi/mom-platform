package io.github.chrisshi.mom.auth.controller.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建业务授权资源的 HTTP 输入。
 *
 * <p>基础长度与必填在 Web 边界校验；唯一性、规范化和引用规则由 Application 负责。
 * 该记录不可变，不携带运行时鉴权含义。</p>
 */
public record CreatePermissionResourceRequest(
    @NotBlank @Size(max = 32) String domainCode,
    @NotBlank @Size(max = 64) String resourceCode,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description,
    @NotNull @Min(0) Integer sortOrder,
    @NotNull Boolean enabled
) {
}
