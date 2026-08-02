package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.application.admin.port.IamAuthorizationAssignmentPort;
import io.github.chrisshi.mom.iam.domain.exception.IamStaleVersionException;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRolePermissionEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserRoleEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRolePermissionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserRoleMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.function.Supplier;

/** 多表授权关系替换 Adapter；不伪装为单表 CrudRepository。 */
public final class MybatisIamAuthorizationAssignmentRepository
        implements IamAuthorizationAssignmentPort {
    private final IamUserMapper userMapper;
    private final IamRoleMapper roleMapper;
    private final IamUserRoleMapper userRoleMapper;
    private final IamRolePermissionMapper rolePermissionMapper;

    public MybatisIamAuthorizationAssignmentRepository(
            IamUserMapper userMapper,
            IamRoleMapper roleMapper,
            IamUserRoleMapper userRoleMapper,
            IamRolePermissionMapper rolePermissionMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public void replaceUserRoles(
            String userId, Collection<String> roleIds, String actor, Instant now,
            Supplier<String> idSupplier) {
        userRoleMapper.deleteByUserId(userId);
        for (String roleId : roleIds) {
            IamUserRoleEntity relation = new IamUserRoleEntity();
            relation.setId(idSupplier.get());
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setStatus(IamRecordStatus.ENABLED);
            relation.setCreatedAt(now);
            relation.setCreatedBy(actor);
            relation.setUpdatedAt(now);
            relation.setUpdatedBy(actor);
            relation.setVersion(0L);
            requireOne(userRoleMapper.insert(relation), "用户角色关系写入失败");
        }
    }

    @Override
    public void advanceUserVersion(
            String userId, long expectedVersion, String actor, Instant now) {
        if (userMapper.advanceVersion(userId, expectedVersion, actor, now) != 1) {
            throw new IamStaleVersionException("version 已过期，请重新读取后重试");
        }
    }

    @Override
    public void replaceRolePermissions(
            String roleId, Collection<String> permissionIds, String actor, Instant now,
            Supplier<String> idSupplier) {
        rolePermissionMapper.deleteByRoleId(roleId);
        for (String permissionId : permissionIds) {
            IamRolePermissionEntity relation = new IamRolePermissionEntity();
            relation.setId(idSupplier.get());
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(actor);
            requireOne(rolePermissionMapper.insert(relation), "角色 Permission 关系写入失败");
        }
    }

    @Override
    public void advanceRoleVersion(
            String roleId, long expectedVersion, String actor, Instant now) {
        if (roleMapper.advanceVersion(roleId, expectedVersion, actor, now) != 1) {
            throw new IamStaleVersionException("version 已过期，请重新读取后重试");
        }
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) throw new IllegalStateException(message);
    }
}
