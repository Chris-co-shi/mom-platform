package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRolePermissionEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserRoleEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRolePermissionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserRoleMapper;
import org.springframework.stereotype.Repository;

/** 用户角色与角色 Permission 关系仓储；不提供 Deny、继承或用户直接 Permission。 */
@Repository
public class IamRoleAssignmentRepository {
    private final IamUserRoleMapper userRoleMapper;
    private final IamRolePermissionMapper rolePermissionMapper;

    /** 创建角色关系仓储。 */
    public IamRoleAssignmentRepository(IamUserRoleMapper userRoleMapper,
            IamRolePermissionMapper rolePermissionMapper) {
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    /** @param assignment 已完成用户类型匹配校验的分配 */
    public void assignRole(IamUserRoleEntity assignment) {
        requireOne(userRoleMapper.insert(assignment), "用户角色分配写入失败");
    }

    /** @param relation 角色与系统 Permission 关系 */
    public void grantPermission(IamRolePermissionEntity relation) {
        requireOne(rolePermissionMapper.insert(relation), "角色 Permission 关系写入失败");
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) throw new IllegalStateException(message);
    }
}
