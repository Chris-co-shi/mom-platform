package io.github.chrisshi.mom.iam.infrastructure.persistence.query;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamPermissionAdminQueryPort;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamPermissionMapper;

import java.util.Collection;
import java.util.List;

/** Permission 目录 MyBatis 查询 Adapter。 */
public final class MybatisIamPermissionAdminQuery implements IamPermissionAdminQueryPort {
    private final IamPermissionMapper mapper;

    public MybatisIamPermissionAdminQuery(IamPermissionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<IamAdminViews.PermissionView> listPermissions(
            String domainCode, int limit, int offset) {
        return mapper.selectAdminPermissions(domainCode, limit, offset);
    }

    @Override
    public List<String> findEnabledPermissionIds(Collection<String> permissionIds) {
        return permissionIds == null || permissionIds.isEmpty()
                ? List.of() : mapper.selectEnabledIds(permissionIds);
    }
}
