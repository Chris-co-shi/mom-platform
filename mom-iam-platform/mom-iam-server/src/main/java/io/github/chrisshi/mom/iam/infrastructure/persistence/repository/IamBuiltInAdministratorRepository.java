package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamPlatformAdministratorPort;
import io.github.chrisshi.mom.iam.application.recovery.IamAdministratorRecoveryPort;
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

/**
 * 内置管理员 Bootstrap、Recovery 与 PLATFORM_ADMIN 不变量的 MyBatis Adapter。
 *
 * <p>该 Infrastructure 类型只依赖 IAM Mapper，并实现 Application/Domain Port；不承担事务、HTTP 或
 * 密码策略。Bootstrap 插入与 Recovery 更新均复用 IAM 唯一 DataSource，写入行数不是 1 时 Fail Closed。
 * 恢复读取通过 Mapper 行锁与版本 CAS 串行化并发实例，密码摘要只作为参数向下传递，不读取、记录或
 * 返回。PLATFORM_ADMIN 的有效性仍由既有用户、角色和关系事实查询判定。</p>
 */
public final class IamBuiltInAdministratorRepository
        implements IamPlatformAdministratorPort, IamAdministratorRecoveryPort {
    private final IamUserMapper userMapper;
    private final IamRoleMapper roleMapper;
    private final IamUserRoleMapper userRoleMapper;

    /**
     * 创建 Adapter。
     *
     * @param userMapper 用户单表 Mapper
     * @param roleMapper 角色单表/Bootstrap 查询 Mapper
     * @param userRoleMapper 用户角色关系 Mapper
     */
    public IamBuiltInAdministratorRepository(
            IamUserMapper userMapper,
            IamRoleMapper roleMapper,
            IamUserRoleMapper userRoleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<IamRole> lockPlatformAdminRole() {
        IamAdminViews.RoleView role = roleMapper.selectBuiltInPlatformAdminForUpdate();
        return Optional.ofNullable(role).map(view -> new IamRole(
                view.id(), view.code(), view.name(), view.applicableUserType(),
                view.status(), view.builtIn(), view.description(), view.version()));
    }

    /**
     * 按固定用户名锁定并读取 Bootstrap 所需非敏感身份。
     *
     * @param username 内置管理员用户名
     * @return 不含凭据材料的身份；不存在时为空
     */
    public Optional<IamUserMapper.BootstrapIdentity> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectBootstrapIdentityByUsername(username));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<AdministratorIdentity> lockByUsername(String username) {
        return findByUsername(username).map(identity -> new AdministratorIdentity(
                identity.id(), identity.username(), identity.userType(), identity.status(),
                identity.systemAccount(), identity.version(), identity.deleted()));
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasEffectivePlatformAdministratorRole(String userId, Instant now) {
        return isEffectivePlatformAdmin(userId, now);
    }

    /** {@inheritDoc} */
    @Override
    public void resetCredential(
            String userId,
            String credentialHash,
            long expectedVersion,
            String actor,
            Instant now) {
        requireOne(userMapper.resetPassword(
                userId, credentialHash, expectedVersion, actor, now),
                "IAM built-in administrator recovery update failed");
    }

    /**
     * 插入固定内置管理员。
     *
     * @param userId 用户 ID
     * @param username 固定用户名
     * @param passwordHash 密码摘要，不得记录
     * @param displayName 展示名称
     * @param actor SYSTEM Actor Code
     * @param now 当前 UTC 时间
     */
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

    /**
     * 为新内置管理员分配 PLATFORM_ADMIN 角色。
     *
     * @param relationId 关系 ID
     * @param userId 用户 ID
     * @param roleId 内置角色 ID
     * @param actor SYSTEM Actor Code
     * @param now 当前 UTC 时间
     */
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

    /** {@inheritDoc} */
    @Override
    public boolean isEffectivePlatformAdmin(String userId, Instant now) {
        return userRoleMapper.existsEffectivePlatformAdmin(userId, now);
    }

    /** {@inheritDoc} */
    @Override
    public int countEffectivePlatformAdministrators(Instant now) {
        return userRoleMapper.countEffectivePlatformAdmins(now);
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) throw new IllegalStateException(message);
    }
}
