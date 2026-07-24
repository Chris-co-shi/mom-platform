package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * IAM 用户明确用途仓储，不暴露万能 CRUD Service。
 *
 * <p>认证状态写入全部委托给带条件的 MyBatis Mapper SQL。登录失败计数使用数据库
 * {@code failed_login_count = failed_login_count + 1} 原子累计，避免并发先查后写丢失更新。</p>
 */
public class IamUserRepository {
    private static final String AUTHENTICATION_ACTOR = "mom-iam-authentication";

    private final IamUserMapper mapper;

    /** @param mapper IAM 用户表 Mapper */
    public IamUserRepository(IamUserMapper mapper) {
        this.mapper = mapper;
    }

    /** @param username 全局唯一用户名 @return 未逻辑删除账号 */
    public Optional<IamUserEntity> findByUsername(String username) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<IamUserEntity>lambdaQuery()
                .eq(IamUserEntity::getUsername, username)));
    }

    /** @param userId 用户 ID @return 未逻辑删除账号 */
    public Optional<IamUserEntity> findById(String userId) {
        return Optional.ofNullable(mapper.selectById(userId));
    }

    /** @param user 已完成领域校验的用户 */
    public void insert(IamUserEntity user) {
        if (mapper.insert(user) != 1) {
            throw new IllegalStateException("IAM 用户写入失败");
        }
    }

    /** @param user 携带 ID 与版本的用户 @return 更新是否成功 */
    public boolean updateById(IamUserEntity user) {
        return mapper.updateById(user) == 1;
    }

    /** 到期锁定在读取认证账号前原子清理。 */
    public void clearExpiredLock(String username, Instant now) {
        mapper.clearExpiredLock(username, now, AUTHENTICATION_ACTOR);
    }

    /** 对可尝试认证的启用账号原子累计失败次数，并在达到阈值时锁定。 */
    public void recordLoginFailure(
            String username, int maximumAttempts, Duration lockDuration, Instant now) {
        mapper.recordLoginFailure(
                username, maximumAttempts, now.plus(lockDuration), now, AUTHENTICATION_ACTOR);
    }

    /** 登录成功后清除失败状态并记录最近登录 UTC 时间。 */
    public void recordLoginSuccess(String username, Instant now) {
        if (mapper.recordLoginSuccess(username, now, AUTHENTICATION_ACTOR) != 1) {
            throw new IllegalStateException("IAM 登录成功状态更新失败");
        }
    }

    /** 首次改密完成后替换摘要并解除 {@code password_change_required}。 */
    public boolean changePassword(String username, String passwordHash, Instant now) {
        return mapper.changePassword(username, passwordHash, now, AUTHENTICATION_ACTOR) == 1;
    }
}
