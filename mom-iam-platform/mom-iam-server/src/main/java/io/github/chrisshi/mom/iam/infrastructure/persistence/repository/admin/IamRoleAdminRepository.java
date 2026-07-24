package io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRoleEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamPermissionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 角色与 Permission 目录的管理仓储；角色可写，Permission 目录只读。 */
public final class IamRoleAdminRepository {
    private final IamRoleMapper roleMapper;
    private final IamPermissionMapper permissionMapper;

    /** 创建角色目录管理仓储。 */
    public IamRoleAdminRepository(IamRoleMapper roleMapper, IamPermissionMapper permissionMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    /** @return 角色管理分页结果 */
    public List<IamAdminViews.RoleView> listRoles(String userType, int limit, int offset) {
        return roleMapper.selectAdminRoles(userType, limit, offset);
    }

    /** @return 持有数据库行锁的角色投影 */
    public Optional<IamAdminViews.RoleView> lockRole(String roleId) {
        return Optional.ofNullable(roleMapper.selectAdminForUpdate(roleId));
    }

    /** @return 输入集合中仍未删除的角色投影 */
    public List<IamAdminViews.RoleView> findRoles(Collection<String> roleIds) {
        return roleIds == null || roleIds.isEmpty()
                ? List.of() : roleMapper.selectAdminByIds(roleIds);
    }

    /** 创建非内置、初始启用的角色。 */
    public void insertRole(
            String id, String code, String name, UserType type, String description,
            String actor, Instant now) {
        IamRoleEntity role = new IamRoleEntity();
        role.setId(id);
        role.setCode(code);
        role.setName(name);
        role.setApplicableUserType(type);
        role.setStatus(IamRecordStatus.ENABLED);
        role.setBuiltIn(Boolean.FALSE);
        role.setDescription(description);
        role.setCreatedAt(now);
        role.setCreatedBy(actor);
        role.setUpdatedAt(now);
        role.setUpdatedBy(actor);
        role.setVersion(0L);
        role.setDeleted(Boolean.FALSE);
        requireOne(roleMapper.insert(role), "角色创建失败");
    }

    /** 按客户端版本更新角色。 */
    public void updateRole(
            String roleId, String name, String description, IamRecordStatus status,
            long version, String actor, Instant now) {
        requireOne(roleMapper.updateAdminRole(
                roleId, name, description, status, version, actor, now), "角色已被并发修改");
    }

    /** @return Permission 目录管理分页结果 */
    public List<IamAdminViews.PermissionView> listPermissions(
            String domainCode, int limit, int offset) {
        return permissionMapper.selectAdminPermissions(domainCode, limit, offset);
    }

    /** @return 输入集合中当前有效且启用的 Permission ID */
    public List<String> findEnabledPermissionIds(Collection<String> permissionIds) {
        return permissionIds == null || permissionIds.isEmpty()
                ? List.of() : permissionMapper.selectEnabledIds(permissionIds);
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
