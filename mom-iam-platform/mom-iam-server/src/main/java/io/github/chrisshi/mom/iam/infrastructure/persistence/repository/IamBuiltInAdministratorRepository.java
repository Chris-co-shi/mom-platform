package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
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
 * 内置管理员初始化与不变量锁仓储。
 *
 * <p>仓储只暴露 Bootstrap 和最后管理员保护需要的精确动作。密码摘要仅作为插入参数传入
 * MyBatis，不提供读取方法；内置角色行的 {@code FOR UPDATE} 锁是所有降权事务的共享串行化点。</p>
 */
public final class IamBuiltInAdministratorRepository {
    private final IamUserMapper userMapper;
    private final IamRoleMapper roleMapper;
    private final IamUserRoleMapper userRoleMapper;

    /**
     * 创建内置管理员仓储。
     *
     * @param userMapper 用户表 Mapper
     * @param roleMapper 角色表 Mapper
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

    /**
     * 锁定内置平台管理员角色，作为 Bootstrap 和所有管理员降权事务的共享数据库锁。
     *
     * @return 内置角色投影；角色不存在时为空
     */
    public Optional<IamAdminViews.RoleView> lockPlatformAdminRole() {
        return Optional.ofNullable(roleMapper.selectBuiltInPlatformAdminForUpdate());
    }

    /**
     * 按用户名读取不含密码摘要的 Bootstrap 身份。
     *
     * @param username 固定用户名
     * @return 已存在账号身份，包含逻辑删除记录
     */
    public Optional<IamUserMapper.BootstrapIdentity> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectBootstrapIdentityByUsername(username));
    }

    /**
     * 插入固定的内置管理员账号。
     *
     * <p>调用方必须处于持有平台管理员角色行锁的本地事务中；该方法不记录或返回密码摘要。</p>
     *
     * @param userId 用户 ID
     * @param username 固定用户名
     * @param passwordHash Spring Security PasswordEncoder 生成的摘要
     * @param displayName 展示名称
     * @param actor 稳定系统操作人
     * @param now 当前 UTC 时间
     */
    public void insertAdministrator(
            String userId,
            String username,
            String passwordHash,
            String displayName,
            String actor,
            Instant now) {
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
     * 为新建内置管理员分配已锁定的 {@code PLATFORM_ADMIN}。
     *
     * @param relationId 关系 ID
     * @param userId 用户 ID
     * @param roleId 内置平台管理员角色 ID
     * @param actor 稳定系统操作人
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
        requireOne(userRoleMapper.insert(relation), "IAM built-in administrator role insert failed");
    }

    /**
     * @param userId 用户 ID
     * @param now 当前 UTC 时间
     * @return 用户是否是当前有效平台管理员
     */
    public boolean isEffectivePlatformAdmin(String userId, Instant now) {
        return userRoleMapper.existsEffectivePlatformAdmin(userId, now);
    }

    /**
     * @param now 当前 UTC 时间
     * @return 当前有效平台管理员数量
     */
    public int countEffectivePlatformAdministrators(Instant now) {
        return userRoleMapper.countEffectivePlatformAdmins(now);
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
