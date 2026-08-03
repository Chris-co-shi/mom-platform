package io.github.chrisshi.mom.iam.bootstrap;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.application.admin.IamSessionRevocationService;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditSink;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserRoleMapper;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 管理员恢复默认关闭、互斥、生产环境 Fail Fast 与提交后撤销测试。 */
class IamAdministratorRecoveryAutoConfigurationTest {

    @Test
    void recoveryMustRemainDisabledByDefault() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ApplicationRunner.class);
        });
    }

    @Test
    void enabledRecoveryMustRejectMissingPasswordDuringContextStartup() {
        runner()
                .withPropertyValues("mom.iam.recovery.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodProfileMustRejectRecoveryDuringContextStartup() {
        runner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues(
                        "mom.iam.recovery.enabled=true",
                        "mom.iam.recovery.password=admin1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bootstrapAndRecoveryMustNotBeEnabledTogether() {
        runner()
                .withPropertyValues(
                        "mom.iam.bootstrap.enabled=true",
                        "mom.iam.bootstrap.password=admin1",
                        "mom.iam.recovery.enabled=true",
                        "mom.iam.recovery.password=admin2")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void safeNonProductionConfigurationMustRegisterSingleRecoveryRunner() {
        runner()
                .withPropertyValues(
                        "mom.iam.recovery.enabled=true",
                        "mom.iam.recovery.password=admin1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IamBuiltInAdministratorRecovery.class);
                    assertThat(context).hasSingleBean(ApplicationRunner.class);
                });
    }

    @Test
    void runnerMustRevokeSessionsOnlyAfterCredentialRecoveryReturns() throws Exception {
        IamAdministratorRecoveryConfiguration configuration =
                new IamAdministratorRecoveryConfiguration();
        IamBuiltInAdministratorRecovery recovery = mock(IamBuiltInAdministratorRecovery.class);
        IamSessionRevocationService revocations = mock(IamSessionRevocationService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<IamSessionRevocationService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(revocations);
        when(recovery.recoverCredential()).thenReturn(
                new IamBuiltInAdministratorRecovery.RecoveryResult("200", false));

        IamAdministratorRecoveryProperties properties = new IamAdministratorRecoveryProperties();
        properties.setEnabled(true);
        properties.setPassword("admin1");
        IamAdministratorBootstrapProperties bootstrap =
                new IamAdministratorBootstrapProperties();

        ApplicationRunner runner = configuration.iamBuiltInAdministratorRecoveryRunner(
                recovery, properties, bootstrap, new MockEnvironment(), provider);
        runner.run(mock(ApplicationArguments.class));

        InOrder order = inOrder(recovery, revocations);
        order.verify(recovery).recoverCredential();
        order.verify(revocations).revokeUserSessions(
                "200", IamBuiltInAdministratorRecovery.SYSTEM_ACTOR,
                "administrator_recovery");
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IamAdministratorBootstrapConfiguration.class,
                        IamAdministratorRecoveryConfiguration.class))
                .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
                .withBean(AuditContextExecutor.class, AuditContextExecutor::new)
                .withBean(IamUserMapper.class, () -> mock(IamUserMapper.class))
                .withBean(IamRoleMapper.class, () -> mock(IamRoleMapper.class))
                .withBean(IamUserRoleMapper.class, () -> mock(IamUserRoleMapper.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
                .withBean(IamSecurityAuditSink.class, () -> mock(IamSecurityAuditSink.class))
                .withBean(IamSecureIdGenerator.class, IamSecureIdGenerator::new)
                .withBean(IamSessionRevocationService.class,
                        () -> mock(IamSessionRevocationService.class))
                .withBean(Clock.class, Clock::systemUTC);
    }
}
