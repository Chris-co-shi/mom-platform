package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.iam.domain.type.ClientChannel;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.RefreshTokenStatus;
import io.github.chrisshi.mom.iam.domain.type.UserSessionStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamOauthClientPolicyEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamAuthorizationCatalogRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSessionRefreshRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Session、Refresh Rotation、重放检测与撤销的行为特征测试。
 *
 * <p>测试在 Repository 边界冻结事务服务的调用顺序与安全状态转换，不替代 PostgreSQL 行锁验证。
 * S08 重构只能调整调用方，不能改变本服务的单 ACTIVE Refresh、绝对期限、重放处置或 revoked sid
 * 失败关闭语义。</p>
 */
@ExtendWith(MockitoExtension.class)
class IamSessionTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-29T03:00:00Z");

    @Mock IamAuthorizationContextLoader contexts;
    @Mock IamAuthorizationCatalogRepository catalog;
    @Mock IamSessionRefreshRepository repository;
    @Mock IamRefreshTokenCodec codec;
    @Mock IamSecureIdGenerator ids;
    @Mock IamRevokedSessionStore revokedSessions;

    private IamSessionTokenService service;

    @BeforeEach
    void setUp() {
        IamSessionProperties properties = new IamSessionProperties();
        service = new IamSessionTokenService(
                contexts, catalog, repository, codec, ids, revokedSessions, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Web 首次签发必须创建八小时绝对 Session、十分钟 Access 和 sequence=1 Refresh。 */
    @Test
    void initialIssueMustPreserveAbsoluteSessionAndPersistenceOrder() {
        IamAuthorizationContext context = context();
        when(contexts.loadByUsername("admin")).thenReturn(context);
        when(catalog.findClientPolicyByClientId("mom-admin-web"))
                .thenReturn(Optional.of(policy(ClientChannel.WEB)));
        when(ids.nextId()).thenReturn("session-1", "refresh-row-1");
        when(codec.generate()).thenReturn("refresh-raw-1");
        when(codec.digest("refresh-raw-1")).thenReturn("refresh-digest-1");

        IamSessionTokenService.InitialIssue issue = service.issueInitial(
                "admin", "mom-admin-web", "127.0.0.1", "JUnit", "browser");

        assertThat(issue.authorization()).isSameAs(context);
        assertThat(issue.sessionId()).isEqualTo("session-1");
        assertThat(issue.refreshToken()).isEqualTo("refresh-raw-1");
        assertThat(issue.issuedAt()).isEqualTo(NOW);
        assertThat(issue.accessExpiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(issue.absoluteExpiresAt()).isEqualTo(NOW.plusSeconds(8 * 60 * 60));
        InOrder order = inOrder(repository);
        order.verify(repository).insertSession(
                "session-1", "user-1", "mom-admin-web", ClientChannel.WEB, NOW,
                NOW.plusSeconds(8 * 60 * 60), NOW.plusSeconds(600),
                "127.0.0.1", "JUnit", "browser");
        order.verify(repository).insertRefresh(
                "refresh-row-1", "session-1", "refresh-digest-1", 1L,
                NOW, NOW.plusSeconds(8 * 60 * 60));
    }

    /** ACTIVE Refresh 必须先消费旧状态，再插入唯一后继并更新 Session。 */
    @Test
    void rotationMustCreateOnlyOneSuccessorAndKeepAbsoluteExpiry() {
        Instant absoluteExpiry = NOW.plusSeconds(3600);
        when(codec.digest("refresh-raw-1")).thenReturn("refresh-digest-1");
        when(repository.lockRefreshByDigest("refresh-digest-1"))
                .thenReturn(Optional.of(refresh(RefreshTokenStatus.ACTIVE, absoluteExpiry)));
        when(repository.lockSession("session-1"))
                .thenReturn(Optional.of(session(UserSessionStatus.ACTIVE, absoluteExpiry)));
        when(contexts.loadByUserId("user-1")).thenReturn(context());
        when(ids.nextId()).thenReturn("refresh-row-2");
        when(codec.generate()).thenReturn("refresh-raw-2");
        when(codec.digest("refresh-raw-2")).thenReturn("refresh-digest-2");

        IamSessionTokenService.Rotation rotation = service.rotate(
                "refresh-raw-1", "mom-admin-web");

        assertThat(rotation.sessionId()).isEqualTo("session-1");
        assertThat(rotation.refreshToken()).isEqualTo("refresh-raw-2");
        assertThat(rotation.sequence()).isEqualTo(2L);
        assertThat(rotation.accessExpiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(rotation.absoluteExpiresAt()).isEqualTo(absoluteExpiry);
        InOrder order = inOrder(repository);
        order.verify(repository).markRotated("refresh-row-1", NOW);
        order.verify(repository).insertRefresh(
                "refresh-row-2", "session-1", "refresh-digest-2", 2L, NOW, absoluteExpiry);
        order.verify(repository).linkReplacement("refresh-row-1", "refresh-row-2");
        order.verify(repository).updateRefreshSuccess("session-1", NOW, NOW.plusSeconds(600));
    }

    /** 已消费 Refresh 再次出现必须终止整个 Session 并写入 revoked sid。 */
    @Test
    void replayMustCompromiseSessionAndRevokeSid() {
        Instant absoluteExpiry = NOW.plusSeconds(3600);
        Instant accessExpiry = NOW.plusSeconds(300);
        when(codec.digest("refresh-replayed")).thenReturn("digest-replayed");
        when(repository.lockRefreshByDigest("digest-replayed"))
                .thenReturn(Optional.of(refresh(RefreshTokenStatus.ROTATED, absoluteExpiry)));
        when(repository.lockSession("session-1"))
                .thenReturn(Optional.of(session(UserSessionStatus.ACTIVE, absoluteExpiry, accessExpiry)));

        assertThatThrownBy(() -> service.rotate("refresh-replayed", "mom-admin-web"))
                .isInstanceOf(IamSessionTokenService.RefreshReplayDetectedException.class)
                .hasMessageContaining("compromised");

        verify(repository).compromise("session-1", NOW, "refresh_token_replay_detected");
        verify(revokedSessions).revoke("session-1", accessExpiry);
        verify(repository, never()).markRotated("refresh-row-1", NOW);
    }

    /** 绝对期限到期必须落库 EXPIRED、撤销 sid，并保持 invalid_grant 对外分类。 */
    @Test
    void expiredSessionMustFailClosedAsInvalidGrant() {
        Instant expiredAt = NOW;
        Instant accessExpiry = NOW.plusSeconds(60);
        when(codec.digest("refresh-expired")).thenReturn("digest-expired");
        when(repository.lockRefreshByDigest("digest-expired"))
                .thenReturn(Optional.of(refresh(RefreshTokenStatus.ACTIVE, expiredAt)));
        when(repository.lockSession("session-1"))
                .thenReturn(Optional.of(session(UserSessionStatus.ACTIVE, expiredAt, accessExpiry)));

        assertThatThrownBy(() -> service.rotate("refresh-expired", "mom-admin-web"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(error -> ((OAuth2AuthenticationException) error).getError().getErrorCode())
                .isEqualTo("invalid_grant");
        verify(repository).markExpired("session-1", NOW);
        verify(revokedSessions).revoke("session-1", accessExpiry);
    }

    /** 显式 Logout/管理员撤销必须先持锁落库，再把 sid 写入撤销存储。 */
    @Test
    void revokeMustPreserveDatabaseThenRevokedStoreOrder() {
        Instant accessExpiry = NOW.plusSeconds(600);
        when(repository.lockSession("session-1"))
                .thenReturn(Optional.of(session(UserSessionStatus.ACTIVE, NOW.plusSeconds(3600), accessExpiry)));

        service.revoke("session-1", "user-1", "self_logout");

        InOrder order = inOrder(repository, revokedSessions);
        order.verify(repository).lockSession("session-1");
        order.verify(repository).revoke("session-1", NOW, "user-1", "self_logout");
        order.verify(revokedSessions).revoke("session-1", accessExpiry);
    }

    private static IamOauthClientPolicyEntity policy(ClientChannel channel) {
        IamOauthClientPolicyEntity policy = new IamOauthClientPolicyEntity();
        policy.setClientId("mom-admin-web");
        policy.setChannel(channel);
        policy.setStatus(IamRecordStatus.ENABLED);
        return policy;
    }

    private static IamSessionRefreshRepository.RefreshRow refresh(
            RefreshTokenStatus status, Instant expiresAt) {
        return new IamSessionRefreshRepository.RefreshRow(
                "refresh-row-1", "session-1", "digest", 1L, status,
                NOW.minusSeconds(60), expiresAt, null, null, null);
    }

    private static IamSessionRefreshRepository.SessionRow session(
            UserSessionStatus status, Instant absoluteExpiry) {
        return session(status, absoluteExpiry, NOW.plusSeconds(600));
    }

    private static IamSessionRefreshRepository.SessionRow session(
            UserSessionStatus status, Instant absoluteExpiry, Instant accessExpiry) {
        return new IamSessionRefreshRepository.SessionRow(
                "session-1", "user-1", "mom-admin-web", ClientChannel.WEB, status,
                NOW.minusSeconds(60), null, absoluteExpiry, accessExpiry, 0L);
    }

    private static IamAuthorizationContext context() {
        return new IamAuthorizationContext(
                "user-1", "admin", "Administrator", UserType.INTERNAL,
                List.of("MOM_ADMIN"), List.of("iam:user:read"), List.of("factory-1"),
                null, null);
    }
}
