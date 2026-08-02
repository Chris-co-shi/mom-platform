package io.github.chrisshi.mom.iam.application.admin.port;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;

import java.util.Collection;
import java.util.List;

/** Permission 目录只读 Port。 */
public interface IamPermissionAdminQueryPort {
    List<IamAdminViews.PermissionView> listPermissions(
            String domainCode, int limit, int offset);
    List<String> findEnabledPermissionIds(Collection<String> permissionIds);
}
