package io.github.chrisshi.mom.auth.controller.response;

import io.github.chrisshi.mom.auth.application.model.PermissionResourceView;
import java.time.Instant;

/**
 * Auth Permission Resource 的 HTTP 响应契约。
 *
 * <p>只暴露资源元数据与版本，不承担运行时授权；不可变且线程安全，Application 失败时不返回部分成功。</p>
 */
public record PermissionResourceResponse(
    String id, String domainCode, String resourceCode, String name, String description,
    int sortOrder, boolean enabled, long version, Instant createdAt, Instant updatedAt
) {
    /**
     * 将用例视图适配成公开响应，无副作用。
     *
     * @param view 资源视图
     * @return HTTP 响应
     */
    public static PermissionResourceResponse from(PermissionResourceView view) {
        return new PermissionResourceResponse(
            view.id(), view.domainCode(), view.resourceCode(), view.name(), view.description(),
            view.sortOrder(), view.enabled(), view.version(), view.createdAt(), view.updatedAt()
        );
    }
}
