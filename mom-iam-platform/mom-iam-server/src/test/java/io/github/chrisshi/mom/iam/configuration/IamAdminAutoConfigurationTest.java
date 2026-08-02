package io.github.chrisshi.mom.iam.configuration;

import io.github.chrisshi.mom.iam.application.admin.*;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditSink;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.*;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamBuiltInAdministratorRepository;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import io.github.chrisshi.mom.iam.web.admin.IamAdminExceptionHandler;
import io.github.chrisshi.mom.iam.web.admin.audit.IamSecurityAuditController;
import io.github.chrisshi.mom.iam.web.admin.client.IamClientAdminController;
import io.github.chrisshi.mom.iam.web.admin.role.IamRoleAdminController;
import io.github.chrisshi.mom.iam.web.admin.session.IamSessionAdminController;
import io.github.chrisshi.mom.iam.web.admin.user.IamUserAdminController;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** IAM Admin 条件化分层 Bean 组装测试。 */
class IamAdminAutoConfigurationTest {

    @Test
    void adminMustRemainDisabledWhenPropertyIsMissing() {
        runnerWithDependencies()
                .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
                .run(this::assertAdminBeansAbsent);
    }

    @Test
    void adminMustRemainDisabledWithoutSqlSessionFactory() {
        runnerWithDependencies()
                .withPropertyValues("mom.iam.admin.enabled=true")
                .run(this::assertAdminBeansAbsent);
    }

    @Test
    void adminMustRegisterApplicationAndSplitControllersWhenEnabled() {
        runnerWithDependencies()
                .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
                .withPropertyValues("mom.iam.admin.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IamUserAdminApplicationService.class);
                    assertThat(context).hasSingleBean(IamUserAuthorizationApplicationService.class);
                    assertThat(context).hasSingleBean(IamRoleAdminApplicationService.class);
                    assertThat(context).hasSingleBean(IamSessionAdminApplicationService.class);
                    assertThat(context).hasSingleBean(IamClientAdminApplicationService.class);
                    assertThat(context).hasSingleBean(IamSecurityAuditQueryService.class);
                    assertThat(context).hasSingleBean(IamUserAdminController.class);
                    assertThat(context).hasSingleBean(IamRoleAdminController.class);
                    assertThat(context).hasSingleBean(IamSessionAdminController.class);
                    assertThat(context).hasSingleBean(IamClientAdminController.class);
                    assertThat(context).hasSingleBean(IamSecurityAuditController.class);
                    assertThat(context).hasSingleBean(IamAdminExceptionHandler.class);
                });
    }

    private WebApplicationContextRunner runnerWithDependencies() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IamAdminConfiguration.class))
                .withBean(IamUserMapper.class, () -> mock(IamUserMapper.class))
                .withBean(IamRoleMapper.class, () -> mock(IamRoleMapper.class))
                .withBean(IamPermissionMapper.class, () -> mock(IamPermissionMapper.class))
                .withBean(IamUserRoleMapper.class, () -> mock(IamUserRoleMapper.class))
                .withBean(IamRolePermissionMapper.class, () -> mock(IamRolePermissionMapper.class))
                .withBean(IamUserFactoryScopeMapper.class,
                        () -> mock(IamUserFactoryScopeMapper.class))
                .withBean(IamUserApplicationMapper.class,
                        () -> mock(IamUserApplicationMapper.class))
                .withBean(IamExternalUserBindingMapper.class,
                        () -> mock(IamExternalUserBindingMapper.class))
                .withBean(IamUserSessionMapper.class,
                        () -> mock(IamUserSessionMapper.class))
                .withBean(IamOauthClientPolicyMapper.class,
                        () -> mock(IamOauthClientPolicyMapper.class))
                .withBean(IamSecurityAuditEventMapper.class,
                        () -> mock(IamSecurityAuditEventMapper.class))
                .withBean(IamBuiltInAdministratorRepository.class,
                        () -> mock(IamBuiltInAdministratorRepository.class))
                .withBean(IamSecurityAuditSink.class,
                        () -> mock(IamSecurityAuditSink.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
                .withBean(IamSessionTokenService.class,
                        () -> mock(IamSessionTokenService.class))
                .withBean(IamSecureIdGenerator.class, IamSecureIdGenerator::new)
                .withBean(Clock.class, Clock::systemUTC);
    }

    private void assertAdminBeansAbsent(AssertableWebApplicationContext context) {
        assertThat(context).hasNotFailed();
        assertThat(context).doesNotHaveBean(IamUserAdminApplicationService.class);
        assertThat(context).doesNotHaveBean(IamUserAdminController.class);
        assertThat(context).doesNotHaveBean(IamAdminExceptionHandler.class);
    }
}
