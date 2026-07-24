package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.ApplicationCode;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamExternalUserBindingEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRoleEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamExternalUserBindingMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRolePermissionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserApplicationMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserFactoryScopeMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserRoleMapper;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * IAM 管理授权聚合只读仓储。
 *
 * <p>该仓储组合表级 MyBatis Mapper 形成应用查询模型，不依赖 JDBC、ResultSet 或持久化实体泄漏。
 * 查询不加锁，只为客户端返回下一次全量替换必须携带的聚合版本；密码、Token、授权码与密钥材料
 * 不在任何 Mapper SELECT 列表中。</p>
 */
public final class IamAdminReadModelRepository {
    private final IamUserMapper userMapper;
    private final IamRoleMapper roleMapper;
    private final IamUserRoleMapper userRoleMapper;
    private final IamRolePermissionMapper rolePermissionMapper;
    private final IamUserFactoryScopeMapper factoryScopeMapper;
    private final IamUserApplicationMapper applicationMapper;
    private final IamExternalUserBindingMapper bindingMapper;

    /** 创建授权聚合只读仓储。 */
    public IamAdminReadModelRepository(
            IamUserMapper userMapper,
            IamRoleMapper roleMapper,
            IamUserRoleMapper userRoleMapper,
            IamRolePermissionMapper rolePermissionMapper,
            IamUserFactoryScopeMapper factoryScopeMapper,
            IamUserApplicationMapper applicationMapper,
            IamExternalUserBindingMapper bindingMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.factoryScopeMapper = factoryScopeMapper;
        this.applicationMapper = applicationMapper;
        this.bindingMapper = bindingMapper;
    }

    /**
     * 读取用户授权聚合的当前版本和全部可管理关系。
     *
     * @throws IamAdminExceptions.NotFound 用户不存在或已逻辑删除
     */
    public IamAdminViews.UserAuthorizationView userAuthorization(String userId) {
        IamAdminViews.UserView user = java.util.Optional.ofNullable(
                userMapper.selectAdminById(userId))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("用户不存在"));
        IamExternalUserBindingEntity binding = bindingMapper.selectByUserId(userId);
        IamAdminViews.PartyBindingView bindingView = binding == null ? null
                : new IamAdminViews.PartyBindingView(
                        binding.getId(), binding.getPartyType(), binding.getPartyId(),
                        binding.getStatus(), binding.getVersion());
        return new IamAdminViews.UserAuthorizationView(
                userId,
                user.version(),
                orderedSet(userRoleMapper.selectRoleIds(userId)),
                orderedSet(factoryScopeMapper.selectFactoryIds(userId)),
                applicationMapper.countEffective(userId, ApplicationCode.MOM_MOBILE_PDA) > 0,
                bindingView);
    }

    /**
     * 读取角色聚合的当前版本和有效 Permission 关系。
     *
     * @throws IamAdminExceptions.NotFound 角色不存在或已逻辑删除
     */
    public IamAdminViews.RolePermissionView rolePermissions(String roleId) {
        IamRoleEntity role = java.util.Optional.ofNullable(roleMapper.selectById(roleId))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("角色不存在"));
        return new IamAdminViews.RolePermissionView(
                roleId, role.getVersion(),
                orderedSet(rolePermissionMapper.selectEnabledPermissionIds(roleId)));
    }

    private static Set<String> orderedSet(Iterable<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.forEach(result::add);
        return Set.copyOf(result);
    }
}
