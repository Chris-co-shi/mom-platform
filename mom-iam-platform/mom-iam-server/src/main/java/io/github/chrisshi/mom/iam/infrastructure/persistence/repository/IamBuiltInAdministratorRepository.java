package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamPlatformAdministratorPort;
import io.github.chrisshi.mom.iam.domain.role.IamRole;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserRoleEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserRoleMapper;

import java.time.Instant;
import java.util.Optional;

/** 内置管理员 Bootstrap 与 PLATFORM_ADMIN 不变量事实 Adapter。 */
public final class IamBuiltInAdministratorRepository
        implements IamPlatformAdministratorPort {
    private final IamUserMapper userMapper;
    private final IamRoleMapper roleMapper;
    private final IamUserRoleMapper userRoleMapper;

    public IamBuiltInAdministratorRepository(
            IamUserMapper userMapper,
            IamRoleMapper roleMapper,
            IamUserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public Optional<IamRole> lockPlatformAdminRole() {
        IamAdminViews.RoleView role = roleMapper.selectBuiltInPlatformAdminForUpdate();
        return Optional.ofNullable(role).map(view -> new IamRole(
                view.id(), view.code(), view.name(), view.applicableUserType(),
                view.status(), view.builtIn(), view.description(), view.version()));
    }

    public Optional<IamUserMapper.BootstrapIdentity> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectBootstrapIdentityByUsername(username));
    }

    public void insertAdministrator(
            String userId, String username, String passwordHash, String displayName,
            String actor, Instant now) {
        IamUserEntity user = new IamUserEntity();
        user.setId(userId);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setDisplayName(displayName);
        user.setUserType(UserType.INTERNAL);
        user.setStatus(IamRecordStatus.ENABLED);
        user.setFailedLoginCount(0);
        user.setPasswordChangeRequired(Boolean.TRUE);
        user.setSystemAccount(Boolean.TRUE);
        user.setCreatedAt(now);
        user.setCreatedBy(actor);
        user.setUpdatedAt(now);
        user.setUpdatedBy(actor);
        user.setVersion(0L);
        user.setDeleted(Boolean.FALSE);
        requireOne(userMapper.insert(user), "IAM built-in administrator insert failed");
    }

    public void assignPlatformAdmin(
            String relationId, String userId, String roleId, String actor, Instant now) {
        IamUserRoleEntity relation = new IamUserRoleEntity();
        relation.setId(relationId);
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        relation.setStatus(IamRecordStatus.ENABLED);
        relation.setCreatedAt(now);
        relation.setCreatedBy(actor);
        relation.setUpdatedAt(now);
        relation.setUpdatedBy(actor);
        relation.setVersion(0L);
        requireOne(userRoleMapper.insert(relation),
                "IAM built-in administrator role insert failed");
    }

    @Override
    public boolean isEffectivePlatformAdmin(String userId, Instant now) {
        return userRoleMapper.existsEffectivePlatformAdmin(userId, now);
    }

    @Override
    public int countEffectivePlatformAdministrators(Instant now) {
        return userRoleMapper.countEffectivePlatformAdmins(now);
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) throw new IllegalStateException(message);
    }
}
