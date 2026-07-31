package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.spring.repository.CrudRepository;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamUserAdminQueryPort;
import io.github.chrisshi.mom.iam.domain.exception.IamStaleVersionException;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccount;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccountRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** MyBatis-Plus IAM User 聚合 Adapter。 */
public class MybatisIamUserAccountRepository
        extends CrudRepository<IamUserMapper, IamUserEntity>
        implements IamUserAccountRepository, IamUserAdminQueryPort {

    @Override
    public Optional<IamUserAccount> findById(String userId) {
        return Optional.ofNullable(getById(userId)).map(MybatisIamUserAccountRepository::toDomain);
    }

    @Override
    public Optional<IamUserAccount> lockById(String userId) {
        return Optional.ofNullable(getBaseMapper().selectAdminForUpdate(userId))
                .map(MybatisIamUserAccountRepository::toDomain);
    }

    @Override
    public void create(
            IamUserAccount account, String passwordHash, String actor, Instant now) {
        IamUserEntity entity = new IamUserEntity();
        entity.setId(account.id());
        entity.setUsername(account.username());
        entity.setPasswordHash(passwordHash);
        entity.setDisplayName(account.displayName());
        entity.setUserType(account.userType());
        entity.setStatus(account.status());
        entity.setFailedLoginCount(account.failedLoginCount());
        entity.setLockedUntil(account.lockedUntil());
        entity.setPasswordChangeRequired(account.passwordChangeRequired());
        entity.setSystemAccount(account.systemAccount());
        entity.setLastLoginAt(account.lastLoginAt());
        entity.setCreatedAt(now);
        entity.setCreatedBy(actor);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(actor);
        entity.setVersion(account.version());
        entity.setDeleted(Boolean.FALSE);
        if (!save(entity)) throw new IllegalStateException("用户创建失败");
    }

    @Override
    public void updateDisplayName(
            String userId, String displayName, long expectedVersion,
            String actor, Instant now) {
        requireOne(getBaseMapper().updateDisplayName(
                userId, displayName, expectedVersion, actor, now), "用户已被并发修改");
    }

    @Override
    public void updateStatus(
            String userId, IamRecordStatus status, long expectedVersion,
            String actor, Instant now) {
        requireOne(getBaseMapper().updateStatus(
                userId, status, expectedVersion, actor, now), "用户状态已被并发修改");
    }

    @Override
    public void unlock(
            String userId, long expectedVersion, String actor, Instant now) {
        requireOne(getBaseMapper().unlock(
                userId, expectedVersion, actor, now), "用户锁定状态已被并发修改");
    }

    @Override
    public void resetCredential(
            String userId, String passwordHash, long expectedVersion,
            String actor, Instant now) {
        requireOne(getBaseMapper().resetPassword(
                userId, passwordHash, expectedVersion, actor, now), "用户密码状态已被并发修改");
    }

    @Override
    public void logicalDelete(
            String userId, long expectedVersion, String actor, Instant now) {
        requireOne(getBaseMapper().logicalDelete(
                userId, expectedVersion, actor, now), "用户已被并发修改");
    }

    @Override
    public List<IamAdminViews.UserView> listUsers(
            String userType, String status, int limit, int offset) {
        return getBaseMapper().selectAdminUsers(userType, status, limit, offset);
    }

    @Override
    public Optional<IamAdminViews.UserView> findUser(String userId) {
        return Optional.ofNullable(getBaseMapper().selectAdminById(userId));
    }

    private static IamUserAccount toDomain(IamUserEntity entity) {
        return new IamUserAccount(
                entity.getId(), entity.getUsername(), entity.getDisplayName(),
                entity.getUserType(), entity.getStatus(),
                entity.getFailedLoginCount() == null ? 0 : entity.getFailedLoginCount(),
                entity.getLockedUntil(),
                Boolean.TRUE.equals(entity.getPasswordChangeRequired()),
                Boolean.TRUE.equals(entity.getSystemAccount()),
                entity.getLastLoginAt(),
                entity.getVersion() == null ? 0L : entity.getVersion());
    }

    private static IamUserAccount toDomain(IamAdminViews.UserView view) {
        return new IamUserAccount(
                view.id(), view.username(), view.displayName(), view.userType(), view.status(),
                view.failedLoginCount(), view.lockedUntil(), view.passwordChangeRequired(),
                view.systemAccount(), view.lastLoginAt(), view.version());
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) throw new IamStaleVersionException(message);
    }
}
