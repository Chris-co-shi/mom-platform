package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.domain.type.ClientChannel;
import io.github.chrisshi.mom.iam.domain.type.RefreshTokenStatus;
import io.github.chrisshi.mom.iam.domain.type.UserSessionStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** S05 Session/Refresh 事务 SQL 仓储；所有 Token 查询只使用 HMAC 摘要。 */
public final class IamSessionRefreshJdbcRepository {
    private static final String SECURITY_ACTOR = "mom-iam-session";

    private final JdbcTemplate jdbc;

    public IamSessionRefreshJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertSession(
            String id,
            String userId,
            String clientId,
            ClientChannel channel,
            Instant loginAt,
            Instant absoluteExpiresAt,
            Instant latestAccessTokenExpiresAt,
            String ipAddress,
            String userAgent,
            String deviceName) {
        int rows = jdbc.update("""
                INSERT INTO iam_user_session (
                    id,user_id,client_id,channel,status,login_at,last_refresh_at,
                    absolute_expires_at,latest_access_token_expires_at,
                    ip_address,user_agent,device_name,
                    created_at,created_by,updated_at,updated_by,version)
                VALUES (?,?,?,?,'ACTIVE',?,NULL,?,?,?,?,?,?,?,?,?,0)
                """,
                id, userId, clientId, channel.name(), timestamp(loginAt),
                timestamp(absoluteExpiresAt), timestamp(latestAccessTokenExpiresAt),
                trim(ipAddress, 64), trim(userAgent, 1000), trim(deviceName, 200),
                timestamp(loginAt), SECURITY_ACTOR, timestamp(loginAt), SECURITY_ACTOR);
        if (rows != 1) throw new IllegalStateException("IAM Session 创建失败");
    }

    public void insertRefresh(
            String id,
            String sessionId,
            String digest,
            long sequence,
            Instant issuedAt,
            Instant expiresAt) {
        int rows = jdbc.update("""
                INSERT INTO iam_refresh_token (
                    id,session_id,token_digest,sequence_no,status,issued_at,expires_at,created_at)
                VALUES (?,?,?,?,'ACTIVE',?,?,?)
                """, id, sessionId, digest, sequence,
                timestamp(issuedAt), timestamp(expiresAt), timestamp(issuedAt));
        if (rows != 1) throw new IllegalStateException("Refresh Token 状态创建失败");
    }

    /** 对摘要记录加行锁，确保同一个 Refresh Token 只能有一个并发请求成功。 */
    public Optional<RefreshRow> lockRefreshByDigest(String digest) {
        List<RefreshRow> rows = jdbc.query("""
                SELECT id,session_id,token_digest,sequence_no,status,issued_at,expires_at,
                       consumed_at,replaced_by_token_id,revoked_at
                  FROM iam_refresh_token
                 WHERE token_digest = ?
                 FOR UPDATE
                """, (resultSet, rowNumber) -> new RefreshRow(
                        resultSet.getString("id"),
                        resultSet.getString("session_id"),
                        resultSet.getString("token_digest"),
                        resultSet.getLong("sequence_no"),
                        RefreshTokenStatus.valueOf(resultSet.getString("status")),
                        resultSet.getTimestamp("issued_at").toInstant(),
                        resultSet.getTimestamp("expires_at").toInstant(),
                        instant(resultSet.getTimestamp("consumed_at")),
                        resultSet.getString("replaced_by_token_id"),
                        instant(resultSet.getTimestamp("revoked_at"))),
                digest);
        return rows.stream().findFirst();
    }

    /** 对 Session 加行锁，Rotation、撤销和重放判定必须持有该锁。 */
    public Optional<SessionRow> lockSession(String sessionId) {
        List<SessionRow> rows = jdbc.query("""
                SELECT id,user_id,client_id,channel,status,login_at,last_refresh_at,
                       absolute_expires_at,latest_access_token_expires_at,version
                  FROM iam_user_session
                 WHERE id = ?
                 FOR UPDATE
                """, (resultSet, rowNumber) -> new SessionRow(
                        resultSet.getString("id"),
                        resultSet.getString("user_id"),
                        resultSet.getString("client_id"),
                        ClientChannel.valueOf(resultSet.getString("channel")),
                        UserSessionStatus.valueOf(resultSet.getString("status")),
                        resultSet.getTimestamp("login_at").toInstant(),
                        instant(resultSet.getTimestamp("last_refresh_at")),
                        resultSet.getTimestamp("absolute_expires_at").toInstant(),
                        instant(resultSet.getTimestamp("latest_access_token_expires_at")),
                        resultSet.getLong("version")),
                sessionId);
        return rows.stream().findFirst();
    }

    /** 先移除旧 ACTIVE 状态，为部分唯一索引释放槽位。 */
    public void markRotated(String tokenId, Instant consumedAt) {
        int rows = jdbc.update("""
                UPDATE iam_refresh_token
                   SET status='ROTATED', consumed_at=?
                 WHERE id=? AND status='ACTIVE'
                """, timestamp(consumedAt), tokenId);
        if (rows != 1) throw new IllegalStateException("Refresh Token 已被并发消费");
    }

    public void linkReplacement(String tokenId, String replacementId) {
        int rows = jdbc.update("""
                UPDATE iam_refresh_token
                   SET replaced_by_token_id=?
                 WHERE id=? AND status='ROTATED'
                """, replacementId, tokenId);
        if (rows != 1) throw new IllegalStateException("Refresh Token 后继关系更新失败");
    }

    public void updateRefreshSuccess(
            String sessionId,
            Instant refreshedAt,
            Instant latestAccessExpiresAt) {
        int rows = jdbc.update("""
                UPDATE iam_user_session
                   SET last_refresh_at=?, latest_access_token_expires_at=?,
                       updated_at=?, updated_by=?, version=version+1
                 WHERE id=? AND status='ACTIVE'
                """, timestamp(refreshedAt), timestamp(latestAccessExpiresAt),
                timestamp(refreshedAt), SECURITY_ACTOR, sessionId);
        if (rows != 1) throw new IllegalStateException("Session Refresh 状态更新失败");
    }

    public void markExpired(String sessionId, Instant now) {
        jdbc.update("""
                UPDATE iam_user_session
                   SET status='EXPIRED', updated_at=?, updated_by=?, version=version+1
                 WHERE id=? AND status='ACTIVE'
                """, timestamp(now), SECURITY_ACTOR, sessionId);
        jdbc.update("""
                UPDATE iam_refresh_token
                   SET status='EXPIRED'
                 WHERE session_id=? AND status='ACTIVE'
                """, sessionId);
    }

    public void compromise(String sessionId, Instant now, String reason) {
        int rows = jdbc.update("""
                UPDATE iam_user_session
                   SET status='COMPROMISED', revoked_at=?, revoked_by=?, revoke_reason=?,
                       updated_at=?, updated_by=?, version=version+1
                 WHERE id=? AND status IN ('ACTIVE','COMPROMISED')
                """, timestamp(now), SECURITY_ACTOR, trim(reason, 1000),
                timestamp(now), SECURITY_ACTOR, sessionId);
        if (rows != 1) throw new IllegalStateException("Session COMPROMISED 更新失败");
        jdbc.update("""
                UPDATE iam_refresh_token
                   SET status='REVOKED', revoked_at=?
                 WHERE session_id=? AND status='ACTIVE'
                """, timestamp(now), sessionId);
    }

    public void revoke(String sessionId, Instant now, String actor, String reason) {
        int rows = jdbc.update("""
                UPDATE iam_user_session
                   SET status='REVOKED', revoked_at=?, revoked_by=?, revoke_reason=?,
                       updated_at=?, updated_by=?, version=version+1
                 WHERE id=? AND status='ACTIVE'
                """, timestamp(now), trim(actor, 128), trim(reason, 1000),
                timestamp(now), trim(actor, 128), sessionId);
        if (rows != 1) throw new IllegalStateException("Session 撤销失败或已终止");
        jdbc.update("""
                UPDATE iam_refresh_token
                   SET status='REVOKED', revoked_at=?
                 WHERE session_id=? AND status='ACTIVE'
                """, timestamp(now), sessionId);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String trim(String value, int maximum) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }

    public record RefreshRow(
            String id,
            String sessionId,
            String tokenDigest,
            long sequence,
            RefreshTokenStatus status,
            Instant issuedAt,
            Instant expiresAt,
            Instant consumedAt,
            String replacedByTokenId,
            Instant revokedAt) {
    }

    public record SessionRow(
            String id,
            String userId,
            String clientId,
            ClientChannel channel,
            UserSessionStatus status,
            Instant loginAt,
            Instant lastRefreshAt,
            Instant absoluteExpiresAt,
            Instant latestAccessTokenExpiresAt,
            long version) {
    }
}
