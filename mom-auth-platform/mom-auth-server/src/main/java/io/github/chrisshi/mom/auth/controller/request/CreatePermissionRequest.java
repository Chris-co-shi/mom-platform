package io.github.chrisshi.mom.auth.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Permission 创建输入只指定 Resource 与 Action；完整 authority code 由服务端生成。
 *
 * <p>Web 校验基础格式，Application 校验资源存在、状态、唯一性与生成结果；不可变且无副作用。</p>
 */
public record CreatePermissionRequest(
    @NotBlank String resourceId,
    @NotBlank @Size(max = 60) String actionCode,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 1000) String description,
    @NotNull Boolean enabled
) {
}
