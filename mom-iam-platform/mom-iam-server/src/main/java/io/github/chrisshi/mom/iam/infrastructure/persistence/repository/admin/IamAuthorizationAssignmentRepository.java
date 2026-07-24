package io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin;

import io.github.chrisshi.mom.iam.admin.IamAdminExceptions;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRolePermissionEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserRoleEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRolePermissionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserRoleMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 用户角色与角色 Permission 全量替换仓储。
 *
 * <p>调用方必须先锁定父用户或角色并验证客户端版本。关系删除、逐项插入和父版本推进共享同一个
 * Spring 本地事务；任一步失败都会回滚，禁止产生关系已替换但父版本未推进的半完成状态。</p>
 */
public final class IamAuthorizationAssignmentRepository {
    private final IamUserMapper userMapper;
    private final IamRoleMapper roleMapper;
    private final IamUserRoleMapper userRoleMapper;
    private final IamRolePermissionMapper rolePermissionMapper;

    /** 创建授权关系管理仓储。 */
    public IamAuthorizationAssignmentRepository(
            IamUserMapper userMapper,
            IamRoleMapper roleMapper,
            IamUserRoleMapper userRoleMapper,
            IamRolePermissionMapper rolePermissionMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    /** @return 用户当前启用角色 ID */
    public Set<String> userRoleIds(String userId) {
        return orderedSet(userRoleMapper.selectRoleIds(userId));
    }

    /** 全量替换用户角色；父行锁和事务由应用服务持有。 */
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

    /** 关系替换后按客户端读取版本推进用户聚合版本。 */
    public void advanceUserVersion(String userId, long version, String actor, Instant now) {
        if (userMapper.advanceVersion(userId, version, actor, now) != 1) {
            throw new IamAdminExceptions.StaleVersion("version 已过期，请重新读取后重试");
        }
    }

    /** @return 角色当前全部 Permission ID */
    public Set<String> rolePermissionIds(String roleId) {
        return orderedSet(rolePermissionMapper.selectPermissionIds(roleId));
    }

    /** 全量替换角色 Permission；父行锁和事务由应用服务持有。 */
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

    /** 关系替换后按客户端读取版本推进角色聚合版本。 */
    public void advanceRoleVersion(String roleId, long version, String actor, Instant now) {
        if (roleMapper.advanceVersion(roleId, version, actor, now) != 1) {
            throw new IamAdminExceptions.StaleVersion("version 已过期，请重新读取后重试");
        }
    }

    /** @return 指定用户是否是当前有效 PLATFORM_ADMIN */
    public boolean userHasEffectivePlatformAdmin(String userId, Instant now) {
        return userRoleMapper.existsEffectivePlatformAdmin(userId, now);
    }

    /** @return 当前有效 PLATFORM_ADMIN 人数 */
    public int effectivePlatformAdminCount(Instant now) {
        return userRoleMapper.countEffectivePlatformAdmins(now);
    }

    private static Set<String> orderedSet(Collection<String> values) {
        return Set.copyOf(new LinkedHashSet<>(values));
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
