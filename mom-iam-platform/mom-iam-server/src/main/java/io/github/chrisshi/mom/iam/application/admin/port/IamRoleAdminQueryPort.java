package io.github.chrisshi.mom.iam.application.admin.port;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;

import java.util.List;
import java.util.Optional;

/** 角色目录只读 Port。 */
public interface IamRoleAdminQueryPort {
    List<IamAdminViews.RoleView> listRoles(String userType, int limit, int offset);
    Optional<IamAdminViews.RoleView> findRole(String roleId);
}
