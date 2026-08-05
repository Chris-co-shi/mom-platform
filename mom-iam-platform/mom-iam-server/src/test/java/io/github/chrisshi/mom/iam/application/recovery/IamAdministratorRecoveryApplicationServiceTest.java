package io.github.chrisshi.mom.iam.application.recovery;

import io.github.chrisshi.mom.iam.application.admin.IamSessionRevocationService;
import io.github.chrisshi.mom.iam.application.admin.model.IamSecurityAuditEvent;
import io.github.chrisshi.mom.iam.application.admin.port.IamIdentifierGenerator;
import io.github.chrisshi.mom.iam.application.admin.port.IamPasswordHasher;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditSink;
import io.github.chrisshi.mom.iam.application.admin.port.IamSessionAdminQueryPort;
import io.github.chrisshi.mom.iam.application.admin.port.IamSessionRevocationPort;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditActorType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 内置管理员恢复用例的安全编排单元测试。
 *
 * <p>测试使用 Port Mock，不启动 Spring、数据库或 Redis；它证明身份不变量、摘要边界、Session 撤销、
 * SYSTEM 审计和失败传播，不能替代 PostgreSQL 行锁与 Redis 真实故障验收。</p>
 */
class IamAdministratorRecoveryApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T02:00:00Z");
    private static final String TEMPORARY_CREDENTIAL = "Local-Recovery-Only-2026!";
    private static final String CREDENTIAL_HASH = "{bcrypt}test-only-hash";

    private final IamAdministratorRecoveryPort administrators =
            mock(IamAdministratorRecoveryPort.class);
    private final IamPasswordHasher passwordHasher = mock(IamPasswordHasher.class);
    private final IamSessionAdminQueryPort sessionQueries = mock(IamSessionAdminQueryPort.class);
    private final IamSessionRevocationPort sessionRevocations =
            mock(IamSessionRevocationPort.class);
    private final IamSecurityAuditSink audits = mock(IamSecurityAuditSink.class);
    private final IamIdentifierGenerator ids = mock(IamIdentifierGenerator.class);

    private IamAdministratorRecoveryApplicationService service;

    @BeforeEach
    void setUp() {
        service = new IamAdministratorRecoveryApplicationService(
                administrators,
                passwordHasher,
                new IamSessionRevocationService(sessionQueries, sessionRevocations),
                audits,
                ids,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recoveryMustResetCredentialRevokeSessionsAndAppendSystemAudit() {
        var administrator = administrator(true, IamRecordStatus.ENABLED, UserType.INTERNAL);
        when(administrators.lockByUsername("admin")).thenReturn(Optional.of(administrator));
        when(administrators.hasEffectivePlatformAdministratorRole("100", NOW)).thenReturn(true);
        when(passwordHasher.hash(TEMPORARY_CREDENTIAL)).thenReturn(CREDENTIAL_HASH);
        when(sessionQueries.activeSessionIdsForUser("100")).thenReturn(List.of("201", "202"));
        when(ids.nextId()).thenReturn("301");

        var result = service.recover(TEMPORARY_CREDENTIAL);

        assertThat(result.revokedSessions()).isEqualTo(2);
        verify(administrators).resetCredential(
                "100", CREDENTIAL_HASH, 7L, "IAM_ADMIN_RECOVERY", NOW);
        verify(sessionRevocations).revoke(
                "201", "IAM_ADMIN_RECOVERY", "administrator_credential_recovery");
        verify(sessionRevocations).revoke(
                "202", "IAM_ADMIN_RECOVERY", "administrator_credential_recovery");

        ArgumentCaptor<IamSecurityAuditEvent> eventCaptor =
                ArgumentCaptor.forClass(IamSecurityAuditEvent.class);
        verify(audits).append(eventCaptor.capture());
        IamSecurityAuditEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("iam.admin.credential-recovered");
        assertThat(event.actorType()).isEqualTo(SecurityAuditActorType.SYSTEM);
        assertThat(event.actorUserId()).isNull();
        assertThat(event.targetId()).isEqualTo("100");
        assertThat(event.changeSummary()).contains("\"revokedSessions\":2");
        assertThat(event.toString())
                .doesNotContain(TEMPORARY_CREDENTIAL)
                .doesNotContain(CREDENTIAL_HASH);
    }

    @Test
    void recoveryMustRejectNonSystemAccountBeforeHashingOrWriting() {
        when(administrators.lockByUsername("admin"))
                .thenReturn(Optional.of(
                        administrator(false, IamRecordStatus.ENABLED, UserType.INTERNAL)));

        assertThatThrownBy(() -> service.recover(TEMPORARY_CREDENTIAL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an enabled built-in INTERNAL account");

        verifyNoInteractions(passwordHasher, sessionQueries, sessionRevocations, audits);
        verify(administrators, never()).resetCredential(any(), any(), anyLong(), any(), any());
    }

    @Test
    void recoveryMustRejectAdministratorWithoutEffectivePlatformRole() {
        when(administrators.lockByUsername("admin"))
                .thenReturn(Optional.of(
                        administrator(true, IamRecordStatus.ENABLED, UserType.INTERNAL)));
        when(administrators.hasEffectivePlatformAdministratorRole("100", NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.recover(TEMPORARY_CREDENTIAL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no effective PLATFORM_ADMIN role");

        verifyNoInteractions(passwordHasher, sessionQueries, sessionRevocations, audits);
    }

    @Test
    void sessionRevocationFailureMustPropagateAndPreventSuccessAudit() {
        when(administrators.lockByUsername("admin"))
                .thenReturn(Optional.of(
                        administrator(true, IamRecordStatus.ENABLED, UserType.INTERNAL)));
        when(administrators.hasEffectivePlatformAdministratorRole("100", NOW)).thenReturn(true);
        when(passwordHasher.hash(TEMPORARY_CREDENTIAL)).thenReturn(CREDENTIAL_HASH);
        when(sessionQueries.activeSessionIdsForUser("100")).thenReturn(List.of("201"));
        doThrow(new IllegalStateException("revocation unavailable"))
                .when(sessionRevocations)
                .revoke("201", "IAM_ADMIN_RECOVERY", "administrator_credential_recovery");

        assertThatThrownBy(() -> service.recover(TEMPORARY_CREDENTIAL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("revocation unavailable");

        verifyNoInteractions(audits);
    }

    @Test
    void recoveryMustRejectCredentialOutsideSecurityLengthBoundary() {
        assertThatThrownBy(() -> service.recover("too-short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12 to 200");
        verifyNoInteractions(administrators, passwordHasher, sessionQueries, audits);
    }

    private static IamAdministratorRecoveryPort.AdministratorIdentity administrator(
            boolean systemAccount, IamRecordStatus status, UserType userType) {
        return new IamAdministratorRecoveryPort.AdministratorIdentity(
                "100", "admin", userType, status, systemAccount, 7L, false);
    }
}
