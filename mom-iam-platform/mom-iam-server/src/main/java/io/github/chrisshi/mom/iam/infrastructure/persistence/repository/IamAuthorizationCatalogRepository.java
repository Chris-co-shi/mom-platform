package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamOauthClientPolicyEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamPermissionEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRoleEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRolePermissionEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamOauthClientPolicyMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamPermissionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRolePermissionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 角色、Permission 与 Client Policy 目录只读仓储；不计算权限并集。 */
@Repository
public class IamAuthorizationCatalogRepository {
    private final IamRoleMapper roleMapper;
    private final IamPermissionMapper permissionMapper;
    private final IamRolePermissionMapper rolePermissionMapper;
    private final IamOauthClientPolicyMapper clientPolicyMapper;

    /** 创建授权目录仓储。 */
    public IamAuthorizationCatalogRepository(IamRoleMapper roleMapper,
            IamPermissionMapper permissionMapper,
            IamRolePermissionMapper rolePermissionMapper,
            IamOauthClientPolicyMapper clientPolicyMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.clientPolicyMapper = clientPolicyMapper;
    }

    /** @param code 角色编码 @return 匹配角色 */
    public Optional<IamRoleEntity> findRoleByCode(String code) {
        return Optional.ofNullable(roleMapper.selectOne(Wrappers.<IamRoleEntity>lambdaQuery()
                .eq(IamRoleEntity::getCode, code)));
    }

    /** @param code Permission Code @return 匹配 Permission */
    public Optional<IamPermissionEntity> findPermissionByCode(String code) {
        return Optional.ofNullable(permissionMapper.selectOne(Wrappers.<IamPermissionEntity>lambdaQuery()
                .eq(IamPermissionEntity::getCode, code)));
    }

    /** @return 全部 Client Policy */
    public List<IamOauthClientPolicyEntity> listClientPolicies() {
        return clientPolicyMapper.selectList(Wrappers.emptyWrapper());
    }

    /** @param roleId 角色 ID @return Permission 关系数量 */
    public long countPermissionsForRole(String roleId) {
        return rolePermissionMapper.selectCount(Wrappers.<IamRolePermissionEntity>lambdaQuery()
                .eq(IamRolePermissionEntity::getRoleId, roleId));
    }
}
