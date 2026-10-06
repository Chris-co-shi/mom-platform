package io.github.chrisshi.mom.auth.controller.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 更新授权资源展示信息的 HTTP 输入。
 *
 * <p>domainCode/resourceCode 为稳定编码，不在此契约中允许重命名；乐观版本由 Application 校验。
 * 该记录不可变，不修改权限或 Role-Permission 关系。</p>
 */
public record UpdatePermissionResourceRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description,
    @NotNull @Min(0) Integer sortOrder,
    @NotNull Boolean enabled,
    @NotNull @PositiveOrZero Long version
) {
}
