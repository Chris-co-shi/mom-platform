package io.github.chrisshi.mom.iam.bootstrap;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.application.admin.model.IamSecurityAuditEvent;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditSink;
import io.github.chrisshi.mom.iam.domain.role.IamRole;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditActorType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamBuiltInAdministratorRepository;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 内置 admin 恢复事务的系统账号、角色、审计与乐观锁边界测试。 */
class IamBuiltInAdministratorRecoveryTest {
    private static final Instant NOW = Instant.parse("2026-08-03T09:00:00Z");

    @Test
    void mustRecoverExistingSystemAdministratorAndAppendSafeSystemAudit() {
        IamBuiltInAdministratorRepository repository = mock(IamBuiltInAdministratorRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        IamSecurityAuditSink auditSink = mock(IamSecurityAuditSink.class);
        IamAdministratorRecoveryProperties properties = properties(false);
        IamUserMapper.BootstrapIdentity administrator = administrator(true, false);
        when(repository.lockPlatformAdminRole()).thenReturn(Optional.of(platformAdminRole()));
        when(repository.lockByUsername("admin")).thenReturn(Optional.of(administrator));
        when(repository.isEffectivePlatformAdmin("200", NOW)).thenReturn(true);
        when(encoder.encode("admin1")).thenReturn("{bcrypt}encoded");

        IamBuiltInAdministratorRecovery recovery = new IamBuiltInAdministratorRecovery(
                repository,
                new AuditContextExecutor(),
                properties,
                encoder,
                auditSink,
                new IamSecureIdGenerator(),
                new MockEnvironment(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        IamBuiltInAdministratorRecovery.RecoveryResult result = recovery.recoverCredential();

        assertThat(result.userId()).isEqualTo("200");
        assertThat(result.forcePasswordChange()).isFalse();
        verify(repository).recoverAdministrator(
                "200", "{bcrypt}encoded", false, 7L,
                IamBuiltInAdministratorRecovery.SYSTEM_ACTOR, NOW);
        ArgumentCaptor<IamSecurityAuditEvent> audit =
                ArgumentCaptor.forClass(IamSecurityAuditEvent.class);
        verify(auditSink).append(audit.capture());
        assertThat(audit.getValue().eventType()).isEqualTo("iam.admin.credential-recovered");
        assertThat(audit.getValue().actorType()).isEqualTo(SecurityAuditActorType.SYSTEM);
        assertThat(audit.getValue().actorClientId())
                .isEqualTo(IamBuiltInAdministratorRecovery.SYSTEM_AUDIT_ACTOR);
        assertThat(audit.getValue().targetId()).isEqualTo("200");
        assertThat(audit.getValue().changeSummary())
                .isEqualTo("{\"forcePasswordChange\":false}");
        assertThat(audit.getValue().reasonDetail()).isNull();
    }

    @Test
    void mustRejectNonSystemAdminIdentity() {
        IamBuiltInAdministratorRepository repository = mock(IamBuiltInAdministratorRepository.class);
        when(repository.lockPlatformAdminRole()).thenReturn(Optional.of(platformAdminRole()));
        when(repository.lockByUsername("admin"))
                .thenReturn(Optional.of(administrator(false, false)));

        IamBuiltInAdministratorRecovery recovery = recovery(repository, properties(false));
        assertThatThrownBy(recovery::recoverCredential)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("system account");
    }

    @Test
    void mustRejectDeletedOrUnassignedAdministrator() {
        IamBuiltInAdministratorRepository repository = mock(IamBuiltInAdministratorRepository.class);
        when(repository.lockPlatformAdminRole()).thenReturn(Optional.of(platformAdminRole()));
        when(repository.lockByUsername("admin"))
                .thenReturn(Optional.of(administrator(true, true)));
        assertThatThrownBy(() -> recovery(repository, properties(false)).recoverCredential())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("undeleted");

        when(repository.lockByUsername("admin"))
                .thenReturn(Optional.of(administrator(true, false)));
        when(repository.isEffectivePlatformAdmin("200", NOW)).thenReturn(false);
        assertThatThrownBy(() -> recovery(repository, properties(false)).recoverCredential())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLATFORM_ADMIN");
    }

    private static IamBuiltInAdministratorRecovery recovery(
            IamBuiltInAdministratorRepository repository,
            IamAdministratorRecoveryProperties properties) {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("admin1")).thenReturn("{bcrypt}encoded");
        return new IamBuiltInAdministratorRecovery(
                repository,
                new AuditContextExecutor(),
                properties,
                encoder,
                mock(IamSecurityAuditSink.class),
                new IamSecureIdGenerator(),
                new MockEnvironment(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static IamAdministratorRecoveryProperties properties(boolean forcePasswordChange) {
        IamAdministratorRecoveryProperties properties = new IamAdministratorRecoveryProperties();
        properties.setEnabled(true);
        properties.setPassword("admin1");
        properties.setForcePasswordChange(forcePasswordChange);
        return properties;
    }

    private static IamRole platformAdminRole() {
        return new IamRole(
                "100", "PLATFORM_ADMIN", "Platform Administrator",
                UserType.INTERNAL, IamRecordStatus.ENABLED,
                true, null, 0L);
    }

    private static IamUserMapper.BootstrapIdentity administrator(
            boolean systemAccount, boolean deleted) {
        return new IamUserMapper.BootstrapIdentity(
                "200", "admin", UserType.INTERNAL, IamRecordStatus.DISABLED,
                true, systemAccount, 5, NOW.plusSeconds(300), 7L, deleted);
    }
}
