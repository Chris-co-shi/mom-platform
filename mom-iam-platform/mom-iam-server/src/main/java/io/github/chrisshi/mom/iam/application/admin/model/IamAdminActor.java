package io.github.chrisshi.mom.iam.application.admin.model;

import io.github.chrisshi.mom.iam.application.admin.IamAdminExceptions;

import java.util.Objects;
import java.util.Set;

/** 从已验证 JWT 映射出的 IAM Admin 调用者快照。 */
public record IamAdminActor(
        String userId,
        String sessionId,
        String clientId,
        Set<String> permissions) {

    public IamAdminActor {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(clientId, "clientId");
        permissions = Set.copyOf(permissions == null ? Set.of() : permissions);
    }

    public void requirePermission(String permission) {
        if (!permissions.contains(permission)) {
            throw new IamAdminExceptions.Forbidden("缺少执行该管理操作的 Permission");
        }
    }
}
