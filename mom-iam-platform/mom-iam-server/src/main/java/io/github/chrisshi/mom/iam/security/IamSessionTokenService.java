package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.iam.domain.type.ClientChannel;
import io.github.chrisshi.mom.iam.domain.type.RefreshTokenStatus;
import io.github.chrisshi.mom.iam.domain.type.UserSessionStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamOauthClientPolicyEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamAuthorizationCatalogRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSessionRefreshRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** S05 用户授权 Session 与 Refresh Rotation 权威服务。 */
public class IamSessionTokenService {
    public static final String REQUEST_REFRESH_TOKEN_ATTRIBUTE =
            IamSessionTokenService.class.getName() + ".refreshToken";
    public static final String REQUEST_SESSION_ID_ATTRIBUTE =
            IamSessionTokenService.class.getName() + ".sessionId";

    private final IamAuthorizationContextService contexts;
    private final IamAuthorizationCatalogRepository catalog;
    private final IamSessionRefreshRepository repository;
    private final IamRefreshTokenCodec codec;
    private final IamSecureIdGenerator ids;
    private final IamRevokedSessionStore revokedSessions;
    private final IamSessionProperties properties;
    private final Clock clock;

    public IamSessionTokenService(
            IamAuthorizationContextService contexts,
            IamAuthorizationCatalogRepository catalog,
            IamSessionRefreshRepository repository,
            IamRefreshTokenCodec codec,
            IamSecureIdGenerator ids,
            IamRevokedSessionStore revokedSessions,
            IamSessionProperties properties,
            Clock clock) {
        this.contexts = contexts;
        this.catalog = catalog;
        this.repository = repository;
        this.codec = codec;
        this.ids = ids;
        this.revokedSessions = revokedSessions;
        this.properties = properties;
        this.clock = clock;
    }

    /** 授权码首次换 Token 时创建绝对 Session 与 sequence=1 Refresh Token。 */
    @Transactional
    public InitialIssue issueInitial(
            String username,
            String clientId,
            String ipAddress,
            String userAgent,
            String deviceName) {
        Instant now = clock.instant();
        IamAuthorizationContext authorization = contexts.loadByUsername(username);
        IamOauthClientPolicyEntity policy = catalog.findClientPolicyByClientId(clientId)
                .orElseThrow(IamSessionTokenService::invalidClient);
        ClientChannel channel = policy.getChannel();
        Duration absoluteTtl = channel == ClientChannel.MOBILE
                ? properties.getMobileAbsoluteTtl() : properties.getWebAbsoluteTtl();
        Instant absoluteExpiresAt = now.plus(absoluteTtl);
        Instant accessExpiresAt = minimum(now.plus(properties.getAccessTokenTtl()), absoluteExpiresAt);

        String sessionId = ids.nextId();
        String refreshToken = codec.generate();
        String refreshId = ids.nextId();
        repository.insertSession(
                sessionId,
                authorization.userId(),
                clientId,
                channel,
                now,
                absoluteExpiresAt,
                accessExpiresAt,
                ipAddress,
                userAgent,
                deviceName);
        repository.insertRefresh(
                refreshId,
                sessionId,
                codec.digest(refreshToken),
                1L,
                now,
                absoluteExpiresAt);
        return new InitialIssue(
                authorization, sessionId, refreshToken, now, accessExpiresAt, absoluteExpiresAt);
    }

    /**
     * 使用行锁消费 ACTIVE Token 并创建唯一后继；任何 ROTATED Token 再次出现都视为重放。
     * OAuth invalid_grant 用于向客户端表达终止状态，但不得回滚已确认的 EXPIRED/COMPROMISED 安全事实。
     */
    @Transactional(noRollbackFor = OAuth2AuthenticationException.class)
    public Rotation rotate(String rawRefreshToken, String clientId) {
        Instant now = clock.instant();
        String digest = codec.digest(rawRefreshToken);
        IamSessionRefreshRepository.RefreshRow token = repository.lockRefreshByDigest(digest)
                .orElseThrow(IamSessionTokenService::invalidGrant);
        IamSessionRefreshRepository.SessionRow session = repository.lockSession(token.sessionId())
                .orElseThrow(IamSessionTokenService::invalidGrant);

        if (!session.clientId().equals(clientId)) {
            throw invalidGrant();
        }
        if (token.status() == RefreshTokenStatus.ROTATED) {
            repository.compromise(session.id(), now, "refresh_token_replay_detected");
            revokedSessions.revoke(session.id(), session.latestAccessTokenExpiresAt());
            throw new RefreshReplayDetectedException(session.id());
        }
        if (token.status() != RefreshTokenStatus.ACTIVE
                || session.status() != UserSessionStatus.ACTIVE) {
            throw invalidGrant();
        }
        if (!now.isBefore(token.expiresAt()) || !now.isBefore(session.absoluteExpiresAt())) {
            repository.markExpired(session.id(), now);
            revokedSessions.revoke(session.id(), session.latestAccessTokenExpiresAt());
            throw invalidGrant();
        }

        IamAuthorizationContext authorization = contexts.loadByUserId(session.userId());
        Instant accessExpiresAt = minimum(now.plus(properties.getAccessTokenTtl()), session.absoluteExpiresAt());
        if (!accessExpiresAt.isAfter(now)) {
            repository.markExpired(session.id(), now);
            revokedSessions.revoke(session.id(), session.latestAccessTokenExpiresAt());
            throw invalidGrant();
        }

        String successorId = ids.nextId();
        String successorToken = codec.generate();
        repository.markRotated(token.id(), now);
        repository.insertRefresh(
                successorId,
                session.id(),
                codec.digest(successorToken),
                token.sequence() + 1L,
                now,
                session.absoluteExpiresAt());
        repository.linkReplacement(token.id(), successorId);
        repository.updateRefreshSuccess(session.id(), now, accessExpiresAt);

        return new Rotation(
                authorization,
                session.id(),
                successorToken,
                now,
                accessExpiresAt,
                session.absoluteExpiresAt(),
                token.sequence() + 1L);
    }

    /** 显式撤销 Session，并将 sid 写入 Redis 至已签发 Access Token 失效。 */
    @Transactional
    public void revoke(String sessionId, String actor, String reason) {
        Instant now = clock.instant();
        IamSessionRefreshRepository.SessionRow session = repository.lockSession(sessionId)
                .orElseThrow(IamSessionTokenService::invalidGrant);
        repository.revoke(sessionId, now, actor, reason);
        revokedSessions.revoke(sessionId, session.latestAccessTokenExpiresAt());
    }

    private static Instant minimum(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static OAuth2AuthenticationException invalidGrant() {
        return new OAuth2AuthenticationException(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_GRANT,
                "Refresh Token 无效、过期、已消费或 Session 已终止",
                null));
    }

    private static OAuth2AuthenticationException invalidClient() {
        return new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }

    public record InitialIssue(
            IamAuthorizationContext authorization,
            String sessionId,
            String refreshToken,
            Instant issuedAt,
            Instant accessExpiresAt,
            Instant absoluteExpiresAt) {
    }

    public record Rotation(
            IamAuthorizationContext authorization,
            String sessionId,
            String refreshToken,
            Instant issuedAt,
            Instant accessExpiresAt,
            Instant absoluteExpiresAt,
            long sequence) {
    }

    /** 检测到旧 Token 重放；不得把 Token 明文附加到异常。 */
    public static final class RefreshReplayDetectedException extends OAuth2AuthenticationException {
        private final String sessionId;

        public RefreshReplayDetectedException(String sessionId) {
            super(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_GRANT,
                    "Refresh Token 重放已导致 Session compromised",
                    null));
            this.sessionId = sessionId;
        }

        public String getSessionId() {
            return sessionId;
        }
    }
}
