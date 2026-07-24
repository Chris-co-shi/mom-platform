package io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 用户管理命令与查询仓储。
 *
 * <p>仓储只暴露 IAM 管理用例需要的动作，不向应用层暴露 Mapper 或密码摘要。所有版本更新都委托给
 * 带 {@code deleted=false AND version=?} 条件的 Mapper SQL，更新失败由上层稳定映射为并发冲突。</p>
 */
public final class IamUserAdminRepository {
    private final IamUserMapper mapper;

    /** @param mapper IAM 用户表受控 Mapper */
    public IamUserAdminRepository(IamUserMapper mapper) {
        this.mapper = mapper;
    }

    /** @return 不含密码摘要的用户分页结果 */
    public List<IamAdminViews.UserView> listUsers(
            String userType, String status, int limit, int offset) {
        return mapper.selectAdminUsers(userType, status, limit, offset);
    }

    /** @return 未删除用户管理投影 */
    public Optional<IamAdminViews.UserView> findUser(String userId) {
        return Optional.ofNullable(mapper.selectAdminById(userId));
    }

    /** @return 持有数据库行锁的用户管理投影 */
    public Optional<IamAdminViews.UserView> lockUser(String userId) {
        return Optional.ofNullable(mapper.selectAdminForUpdate(userId));
    }

    /** 创建初始为 ENABLED 且要求首次改密的 IAM 用户。 */
    public void insertUser(
            String id, String username, String passwordHash, String displayName,
            UserType userType, String actor, Instant now) {
        IamUserEntity user = new IamUserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setDisplayName(displayName);
        user.setUserType(userType);
        user.setStatus(IamRecordStatus.ENABLED);
        user.setFailedLoginCount(0);
        user.setPasswordChangeRequired(Boolean.TRUE);
        user.setCreatedAt(now);
        user.setCreatedBy(actor);
        user.setUpdatedAt(now);
        user.setUpdatedBy(actor);
        user.setVersion(0L);
        user.setDeleted(Boolean.FALSE);
        requireOne(mapper.insert(user), "用户创建失败");
    }

    /** 按客户端版本更新展示名。 */
    public void updateDisplayName(
            String userId, String displayName, long version, String actor, Instant now) {
        requireOne(mapper.updateDisplayName(userId, displayName, version, actor, now),
                "用户已被并发修改");
    }

    /** 按客户端版本更新账号状态。 */
    public void updateUserStatus(
            String userId, IamRecordStatus status, long version, String actor, Instant now) {
        requireOne(mapper.updateStatus(userId, status, version, actor, now),
                "用户状态已被并发修改");
    }

    /** 按客户端版本清除临时锁定。 */
    public void unlockUser(String userId, long version, String actor, Instant now) {
        requireOne(mapper.unlock(userId, version, actor, now),
                "用户锁定状态已被并发修改");
    }

    /** 按客户端版本重置密码状态。 */
    public void resetPassword(
            String userId, String passwordHash, long version, String actor, Instant now) {
        requireOne(mapper.resetPassword(userId, passwordHash, version, actor, now),
                "用户密码状态已被并发修改");
    }

    /** 按客户端版本逻辑删除并禁用用户。 */
    public void logicalDeleteUser(String userId, long version, String actor, Instant now) {
        requireOne(mapper.logicalDelete(userId, version, actor, now),
                "用户已被并发修改");
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
