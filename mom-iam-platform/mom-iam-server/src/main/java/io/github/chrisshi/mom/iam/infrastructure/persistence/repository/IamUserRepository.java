package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** IAM 用户明确用途仓储，不暴露万能 CRUD Service。 */
public class IamUserRepository {
    private static final String AUTHENTICATION_ACTOR = "mom-iam-authentication";

    private final IamUserMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    /** 创建用户仓储。 */
    public IamUserRepository(IamUserMapper mapper, JdbcTemplate jdbcTemplate) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
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
        if (mapper.insert(user) != 1) throw new IllegalStateException("IAM 用户写入失败");
    }

    /** @param user 携带 ID 与版本的用户 @return 更新是否成功 */
    public boolean updateById(IamUserEntity user) { return mapper.updateById(user) == 1; }

    /**
     * 到期锁定在读取认证账号前原子清理。该 JDBC 路径显式维护 S01 审计字段。
     */
    public void clearExpiredLock(String username, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        jdbcTemplate.update("""
                UPDATE iam_user
                SET failed_login_count = 0,
                    locked_until = NULL,
                    updated_at = ?,
                    updated_by = ?,
                    version = version + 1
                WHERE username = ?
                  AND deleted = false
                  AND locked_until IS NOT NULL
                  AND locked_until <= ?
                """, timestamp, AUTHENTICATION_ACTOR, username, timestamp);
    }

    /**
     * 对存在且当前可尝试认证的启用账号累计失败次数；达到阈值后设置临时锁定。
     */
    public void recordLoginFailure(String username, int maximumAttempts, Duration lockDuration, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        Timestamp lockedUntil = Timestamp.from(now.plus(lockDuration));
        jdbcTemplate.update("""
                UPDATE iam_user
                SET failed_login_count = failed_login_count + 1,
                    locked_until = CASE
                        WHEN failed_login_count + 1 >= ? THEN ?
                        ELSE locked_until
                    END,
                    updated_at = ?,
                    updated_by = ?,
                    version = version + 1
                WHERE username = ?
                  AND status = 'ENABLED'
                  AND deleted = false
                  AND (locked_until IS NULL OR locked_until <= ?)
                """, maximumAttempts, lockedUntil, timestamp,
                AUTHENTICATION_ACTOR, username, timestamp);
    }

    /** 登录成功后清除失败状态并记录最近登录 UTC 时间。 */
    public void recordLoginSuccess(String username, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        int rows = jdbcTemplate.update("""
                UPDATE iam_user
                SET failed_login_count = 0,
                    locked_until = NULL,
                    last_login_at = ?,
                    updated_at = ?,
                    updated_by = ?,
                    version = version + 1
                WHERE username = ?
                  AND status = 'ENABLED'
                  AND deleted = false
                """, timestamp, timestamp, AUTHENTICATION_ACTOR, username);
        if (rows != 1) {
            throw new IllegalStateException("IAM 登录成功状态更新失败");
        }
    }

    /** 首次改密完成后替换摘要并解除 password_change_required。 */
    public boolean changePassword(String username, String passwordHash, Instant now) {
        return jdbcTemplate.update("""
                UPDATE iam_user
                SET password_hash = ?,
                    password_change_required = false,
                    failed_login_count = 0,
                    locked_until = NULL,
                    updated_at = ?,
                    updated_by = ?,
                    version = version + 1
                WHERE username = ?
                  AND status = 'ENABLED'
                  AND deleted = false
                """, passwordHash, Timestamp.from(now), AUTHENTICATION_ACTOR, username) == 1;
    }
}
