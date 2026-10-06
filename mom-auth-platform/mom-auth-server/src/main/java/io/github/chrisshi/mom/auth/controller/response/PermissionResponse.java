package io.github.chrisshi.mom.auth.controller.response;

import io.github.chrisshi.mom.auth.application.model.PermissionView;

import java.time.Instant;

/**
 * 带权威 Resource 元数据的 Permission HTTP 响应。
 *
 * <p>仅 Permission code 参与鉴权；资源名称是展示信息，客户端不得解析 code 推断归属。
 * 不可变、线程安全，不包含持久化内部字段。</p>
 */
public record PermissionResponse(
    String id,
    String resourceId,
    String domainCode,
    String resourceCode,
    String resourceName,
    String actionCode,
    String code,
    String name,
    String description,
    boolean enabled,
    long version,
    Instant createdAt,
    Instant updatedAt
) {
    /**
     * 将用例视图适配成响应，无副作用。
     *
     * @param view Permission 视图
     * @return HTTP 响应
     */
    public static PermissionResponse from(PermissionView view) {
        return new PermissionResponse(
            view.id(), view.resourceId(), view.domainCode(), view.resourceCode(),
            view.resourceName(), view.actionCode(), view.code(), view.name(), view.description(), view.enabled(),
            view.version(), view.createdAt(), view.updatedAt()
        );
    }
}
