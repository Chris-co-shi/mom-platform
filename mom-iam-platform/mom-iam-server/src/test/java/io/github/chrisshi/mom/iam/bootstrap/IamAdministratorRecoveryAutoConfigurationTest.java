package io.github.chrisshi.mom.iam.bootstrap;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.application.admin.port.IamIdentifierGenerator;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditSink;
import io.github.chrisshi.mom.iam.application.recovery.IamAdministratorRecoveryApplicationService;
import io.github.chrisshi.mom.iam.application.recovery.IamAdministratorRecoveryPort;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserSessionMapper;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员恢复自动配置的条件装配和启动期 Fail Closed 测试。
 *
 * <p>测试不执行 Runner 或数据库写入，只验证禁用时无写能力、显式安全配置可装配，以及生产或 Bootstrap
 * 冲突会在上下文启动阶段失败。</p>
 */
class IamAdministratorRecoveryAutoConfigurationTest {

    @Test
    void recoveryMustRemainDisabledByDefault() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IamAdministratorRecoveryApplicationService.class);
            assertThat(context).doesNotHaveBean(ApplicationRunner.class);
        });
    }

    @Test
    void safeExplicitConfigurationMustRegisterRecoveryAndRunner() {
        runner().withPropertyValues(safeProperties()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(IamAdministratorRecoveryApplicationService.class);
            assertThat(context).hasSingleBean(ApplicationRunner.class);
        });
    }

    @Test
    void productionProfileMustFailContextStartup() {
        runner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"))
                .withPropertyValues(safeProperties())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void simultaneousBootstrapMustFailContextStartup() {
        runner()
                .withPropertyValues(safeProperties())
                .withPropertyValues(
                        "mom.iam.bootstrap.enabled=true",
                        "mom.iam.bootstrap.password=Bootstrap-Test-Secret-2026!")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void runnerMustClearTemporaryCredentialAfterRecoveryFailure() {
        var recovery = mock(IamAdministratorRecoveryApplicationService.class);
        var properties = enabledRecoveryProperties();
        var bootstrap = new IamAdministratorBootstrapProperties();
        var environment = new org.springframework.mock.env.MockEnvironment();
        when(recovery.recover("Test-Recovery-Secret-2026!"))
                .thenThrow(new IllegalStateException("recovery failed"));
        ApplicationRunner runner = new IamAdministratorRecoveryConfiguration()
                .iamAdministratorRecoveryRunner(
                        recovery,
                        properties,
                        bootstrap,
                        new AuditContextExecutor(),
                        environment);

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("recovery failed");
        verify(recovery).recover("Test-Recovery-Secret-2026!");
        assertThat(properties.getPassword()).isEmpty();
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IamAdministratorRecoveryConfiguration.class))
                .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
                .withBean(IamAdministratorRecoveryPort.class,
                        () -> mock(IamAdministratorRecoveryPort.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
                .withBean(IamUserSessionMapper.class, () -> mock(IamUserSessionMapper.class))
                .withBean(IamSessionTokenService.class, () -> mock(IamSessionTokenService.class))
                .withBean(IamSecurityAuditSink.class, () -> mock(IamSecurityAuditSink.class))
                .withBean(IamIdentifierGenerator.class, () -> mock(IamIdentifierGenerator.class))
                .withBean(AuditContextExecutor.class, AuditContextExecutor::new)
                .withBean(Clock.class, Clock::systemUTC);
    }

    private static String[] safeProperties() {
        return new String[] {
                "mom.iam.recovery.enabled=true",
                "mom.iam.recovery.password=Test-Recovery-Secret-2026!",
                "mom.iam.recovery.confirmation=RESET_ADMIN_CREDENTIAL",
                "mom.iam.recovery.force-password-change=true"
        };
    }

    private static IamAdministratorRecoveryProperties enabledRecoveryProperties() {
        var properties = new IamAdministratorRecoveryProperties();
        properties.setEnabled(true);
        properties.setPassword("Test-Recovery-Secret-2026!");
        properties.setConfirmation(IamAdministratorRecoveryProperties.REQUIRED_CONFIRMATION);
        return properties;
    }
}
