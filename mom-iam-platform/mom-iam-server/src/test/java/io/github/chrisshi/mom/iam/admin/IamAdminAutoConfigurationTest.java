package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamExternalUserBindingMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamOauthClientPolicyMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamPermissionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRolePermissionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamSecurityAuditEventMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserApplicationMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserFactoryScopeMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserSessionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSecurityAuditEventAppender;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamUserAdminRepository;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** IAM 管理自动配置显式开关、MyBatis 条件和完整 Web Bean 组装回归测试。 */
class IamAdminAutoConfigurationTest {

    @Test
    void adminMustRemainDisabledWhenPropertyIsMissing() {
        runnerWithDependencies()
                .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
                .run(this::assertAdminBeansAbsent);
    }

    @Test
    void adminMustRemainDisabledWhenExplicitlyDisabled() {
        runnerWithDependencies()
                .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
                .withPropertyValues("mom.iam.admin.enabled=false")
                .run(this::assertAdminBeansAbsent);
    }

    @Test
    void adminMustRemainDisabledWithoutSqlSessionFactory() {
        runnerWithDependencies()
                .withPropertyValues("mom.iam.admin.enabled=true")
                .run(this::assertAdminBeansAbsent);
    }

    @Test
    void adminMustRegisterRepositoryServiceAndControllerWhenEnabledWithMyBatis() {
        runnerWithDependencies()
                .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
                .withPropertyValues("mom.iam.admin.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IamUserAdminRepository.class);
                    assertThat(context).hasSingleBean(IamAdminReadModelRepository.class);
                    assertThat(context).hasSingleBean(IamAdminService.class);
                    assertThat(context).hasSingleBean(IamAdminController.class);
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
                .withBean(IamUserSessionMapper.class, () -> mock(IamUserSessionMapper.class))
                .withBean(IamOauthClientPolicyMapper.class,
                        () -> mock(IamOauthClientPolicyMapper.class))
                .withBean(IamSecurityAuditEventMapper.class,
                        () -> mock(IamSecurityAuditEventMapper.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
                .withBean(IamSessionTokenService.class, () -> mock(IamSessionTokenService.class))
                .withBean(IamSecurityAuditEventAppender.class,
                        () -> mock(IamSecurityAuditEventAppender.class))
                .withBean(IamSecureIdGenerator.class, IamSecureIdGenerator::new)
                .withBean(Clock.class, Clock::systemUTC);
    }

    private void assertAdminBeansAbsent(AssertableWebApplicationContext context) {
        assertThat(context).hasNotFailed();
        assertThat(context).doesNotHaveBean(IamUserAdminRepository.class);
        assertThat(context).doesNotHaveBean(IamAdminReadModelRepository.class);
        assertThat(context).doesNotHaveBean(IamAdminService.class);
        assertThat(context).doesNotHaveBean(IamAdminController.class);
        assertThat(context).doesNotHaveBean(IamAdminExceptionHandler.class);
    }
}
