package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.domain.type.ClientChannel;
import io.github.chrisshi.mom.iam.domain.type.RefreshTokenStatus;
import io.github.chrisshi.mom.iam.domain.type.UserSessionStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRefreshTokenEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserSessionEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRefreshTokenMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserSessionMapper;

import java.time.Instant;
import java.util.Optional;

/**
 * Session 与 Refresh Rotation 事务仓储。
 *
 * <p>所有 Token 查询只接受 HMAC 摘要。摘要行与 Session 行均通过 Mapper 的 {@code FOR UPDATE}
 * 查询加锁；旧 Token 状态迁移、新 Token 插入、后继链接、重放处置和 Session 更新由应用服务
 * 的 Spring 本地事务统一提交或回滚。</p>
 */
public final class IamSessionRefreshRepository {
    private static final String SECURITY_ACTOR = "mom-iam-session";

    private final IamUserSessionMapper sessionMapper;
    private final IamRefreshTokenMapper refreshMapper;

    /** 创建 Session/Refresh 事务仓储。 */
    public IamSessionRefreshRepository(
            IamUserSessionMapper sessionMapper, IamRefreshTokenMapper refreshMapper) {
        this.sessionMapper = sessionMapper;
        this.refreshMapper = refreshMapper;
    }

    /** 插入初始 ACTIVE Session。 */
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
        IamUserSessionEntity session = new IamUserSessionEntity();
        session.setId(id);
        session.setUserId(userId);
        session.setClientId(clientId);
        session.setChannel(channel);
        session.setStatus(UserSessionStatus.ACTIVE);
        session.setLoginAt(loginAt);
        session.setAbsoluteExpiresAt(absoluteExpiresAt);
        session.setLatestAccessTokenExpiresAt(latestAccessTokenExpiresAt);
        session.setIpAddress(trim(ipAddress, 64));
        session.setUserAgent(trim(userAgent, 1000));
        session.setDeviceName(trim(deviceName, 200));
        session.setCreatedAt(loginAt);
        session.setCreatedBy(SECURITY_ACTOR);
        session.setUpdatedAt(loginAt);
        session.setUpdatedBy(SECURITY_ACTOR);
        session.setVersion(0L);
        requireOne(sessionMapper.insertAuthenticationSession(session, SECURITY_ACTOR),
                "IAM Session 创建失败");
    }

    /** 插入只包含摘要的 ACTIVE Refresh Token 状态。 */
    public void insertRefresh(
            String id, String sessionId, String digest, long sequence,
            Instant issuedAt, Instant expiresAt) {
        IamRefreshTokenEntity refresh = new IamRefreshTokenEntity();
        refresh.setId(id);
        refresh.setSessionId(sessionId);
        refresh.setTokenDigest(digest);
        refresh.setSequenceNo(sequence);
        refresh.setStatus(RefreshTokenStatus.ACTIVE);
        refresh.setIssuedAt(issuedAt);
        refresh.setExpiresAt(expiresAt);
        refresh.setCreatedAt(issuedAt);
        requireOne(refreshMapper.insertAuthenticationRefresh(refresh),
                "Refresh Token 状态创建失败");
    }

    /** @return 按摘要持有行锁的 Refresh 状态 */
    public Optional<RefreshRow> lockRefreshByDigest(String digest) {
        return Optional.ofNullable(refreshMapper.selectForUpdateByDigest(digest))
                .map(IamSessionRefreshRepository::refreshRow);
    }

    /** @return 持有行锁的 Session 状态 */
    public Optional<SessionRow> lockSession(String sessionId) {
        return Optional.ofNullable(sessionMapper.selectForUpdate(sessionId))
                .map(IamSessionRefreshRepository::sessionRow);
    }

    /** 先移除旧 ACTIVE 状态，为部分唯一索引释放槽位。 */
    public void markRotated(String tokenId, Instant consumedAt) {
        requireOne(refreshMapper.markRotated(tokenId, consumedAt), "Refresh Token 已被并发消费");
    }

    /** 记录旧 Token 的后继状态 ID。 */
    public void linkReplacement(String tokenId, String replacementId) {
        requireOne(refreshMapper.linkReplacement(tokenId, replacementId),
                "Refresh Token 后继关系更新失败");
    }

    /** Refresh 成功后更新 Session 最近刷新与 Access Token 过期时间。 */
    public void updateRefreshSuccess(
            String sessionId, Instant refreshedAt, Instant latestAccessExpiresAt) {
        requireOne(sessionMapper.updateRefreshSuccess(
                sessionId, refreshedAt, latestAccessExpiresAt, SECURITY_ACTOR),
                "Session Refresh 状态更新失败");
    }

    /** 将 ACTIVE Session 和 ACTIVE Refresh Token 置为 EXPIRED。 */
    public void markExpired(String sessionId, Instant now) {
        sessionMapper.markExpired(sessionId, now, SECURITY_ACTOR);
        refreshMapper.expireActiveBySession(sessionId);
    }

    /** 重放检测后将 Session 置为 COMPROMISED，并撤销全部 ACTIVE Refresh Token。 */
    public void compromise(String sessionId, Instant now, String reason) {
        requireOne(sessionMapper.compromise(
                sessionId, now, SECURITY_ACTOR, trim(reason, 1000)),
                "Session COMPROMISED 更新失败");
        refreshMapper.revokeActiveBySession(sessionId, now);
    }

    /** 撤销 ACTIVE Session，并撤销全部 ACTIVE Refresh Token。 */
    public void revoke(String sessionId, Instant now, String actor, String reason) {
        String normalizedActor = trim(actor, 128);
        requireOne(sessionMapper.revoke(
                sessionId, now, normalizedActor, trim(reason, 1000)),
                "Session 撤销失败或已终止");
        refreshMapper.revokeActiveBySession(sessionId, now);
    }

    private static RefreshRow refreshRow(IamRefreshTokenEntity entity) {
        return new RefreshRow(
                entity.getId(), entity.getSessionId(), entity.getTokenDigest(),
                entity.getSequenceNo(), entity.getStatus(), entity.getIssuedAt(),
                entity.getExpiresAt(), entity.getConsumedAt(), entity.getReplacedByTokenId(),
                entity.getRevokedAt());
    }

    private static SessionRow sessionRow(IamUserSessionEntity entity) {
        return new SessionRow(
                entity.getId(), entity.getUserId(), entity.getClientId(), entity.getChannel(),
                entity.getStatus(), entity.getLoginAt(), entity.getLastRefreshAt(),
                entity.getAbsoluteExpiresAt(), entity.getLatestAccessTokenExpiresAt(),
                entity.getVersion());
    }

    private static String trim(String value, int maximum) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maximum
                ? normalized : normalized.substring(0, maximum);
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }

    /** Refresh Rotation 内部状态；只在安全服务内使用。 */
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

    /** Session Rotation 与撤销内部状态；不包含设备指纹或凭证材料。 */
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
