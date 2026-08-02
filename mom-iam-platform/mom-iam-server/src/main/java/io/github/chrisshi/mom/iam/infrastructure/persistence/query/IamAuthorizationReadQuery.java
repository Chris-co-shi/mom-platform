package io.github.chrisshi.mom.iam.infrastructure.persistence.query;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamAuthorizationReadPort;
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
import java.util.Optional;
import java.util.Set;

/** IAM 管理授权聚合只读 Adapter。 */
public final class IamAuthorizationReadQuery
        implements IamAuthorizationReadPort {
    private final IamUserMapper userMapper;
    private final IamRoleMapper roleMapper;
    private final IamUserRoleMapper userRoleMapper;
    private final IamRolePermissionMapper rolePermissionMapper;
    private final IamUserFactoryScopeMapper factoryScopeMapper;
    private final IamUserApplicationMapper applicationMapper;
    private final IamExternalUserBindingMapper bindingMapper;

    public IamAuthorizationReadQuery(
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

    @Override
    public Optional<IamAdminViews.UserAuthorizationView> userAuthorization(String userId) {
        IamAdminViews.UserView user = userMapper.selectAdminById(userId);
        if (user == null) return Optional.empty();
        IamExternalUserBindingEntity binding = bindingMapper.selectOne(
                Wrappers.<IamExternalUserBindingEntity>lambdaQuery()
                        .eq(IamExternalUserBindingEntity::getUserId, userId));
        IamAdminViews.PartyBindingView bindingView = binding == null ? null
                : new IamAdminViews.PartyBindingView(
                        binding.getId(), binding.getPartyType(), binding.getPartyId(),
                        binding.getStatus(), binding.getVersion());
        return Optional.of(new IamAdminViews.UserAuthorizationView(
                userId,
                user.version(),
                orderedSet(userRoleMapper.selectRoleIds(userId)),
                orderedSet(factoryScopeMapper.selectFactoryIds(userId)),
                applicationMapper.countEffective(
                        userId, ApplicationCode.MOM_MOBILE_PDA) > 0,
                bindingView));
    }

    @Override
    public Optional<IamAdminViews.RolePermissionView> rolePermissions(String roleId) {
        IamRoleEntity role = roleMapper.selectById(roleId);
        if (role == null) return Optional.empty();
        return Optional.of(new IamAdminViews.RolePermissionView(
                roleId, role.getVersion(),
                orderedSet(rolePermissionMapper.selectEnabledPermissionIds(roleId))));
    }

    private static Set<String> orderedSet(Iterable<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.forEach(result::add);
        return Set.copyOf(result);
    }
}
