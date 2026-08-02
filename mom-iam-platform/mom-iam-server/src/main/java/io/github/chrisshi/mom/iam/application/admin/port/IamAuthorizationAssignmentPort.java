package io.github.chrisshi.mom.iam.application.admin.port;

import java.time.Instant;
import java.util.Collection;
import java.util.function.Supplier;

/** 用户角色、角色 Permission 与父聚合版本推进 Port。 */
public interface IamAuthorizationAssignmentPort {
    void replaceUserRoles(
            String userId, Collection<String> roleIds, String actor, Instant now,
            Supplier<String> idSupplier);
    void advanceUserVersion(
            String userId, long expectedVersion, String actor, Instant now);
    void replaceRolePermissions(
            String roleId, Collection<String> permissionIds, String actor, Instant now,
            Supplier<String> idSupplier);
    void advanceRoleVersion(
            String roleId, long expectedVersion, String actor, Instant now);
}
