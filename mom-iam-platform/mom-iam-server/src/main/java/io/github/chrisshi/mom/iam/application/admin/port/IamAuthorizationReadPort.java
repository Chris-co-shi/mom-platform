package io.github.chrisshi.mom.iam.application.admin.port;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;

import java.util.Optional;

/** 用户授权与角色 Permission 聚合查询 Port。 */
public interface IamAuthorizationReadPort {
    Optional<IamAdminViews.UserAuthorizationView> userAuthorization(String userId);
    Optional<IamAdminViews.RolePermissionView> rolePermissions(String roleId);
}
