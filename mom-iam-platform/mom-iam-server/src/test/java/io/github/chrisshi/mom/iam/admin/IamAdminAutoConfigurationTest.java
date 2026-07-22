package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSecurityAuditEventAppender;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** S07 IAM 管理自动配置显式开关、JdbcTemplate 条件和完整 Web Bean 组装回归测试。 */
class IamAdminAutoConfigurationTest {

    @Test
    void adminMustRemainDisabledWhenPropertyIsMissing() {
        runnerWithDependencies()
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .run(this::assertAdminBeansAbsent);
    }

    @Test
    void adminMustRemainDisabledWhenExplicitlyDisabled() {
        runnerWithDependencies()
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withPropertyValues("mom.iam.admin.enabled=false")
                .run(this::assertAdminBeansAbsent);
    }

    @Test
    void adminMustRemainDisabledWithoutJdbcTemplate() {
        runnerWithDependencies()
                .withPropertyValues("mom.iam.admin.enabled=true")
                .run(this::assertAdminBeansAbsent);
    }

    @Test
    void adminMustRegisterRepositoryServiceAndControllerWhenEnabledWithJdbcTemplate() {
        runnerWithDependencies()
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withPropertyValues("mom.iam.admin.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IamAdminJdbcRepository.class);
                    assertThat(context).hasSingleBean(IamAdminReadModelRepository.class);
                    assertThat(context).hasSingleBean(IamAdminService.class);
                    assertThat(context).hasSingleBean(IamAdminController.class);
                    assertThat(context).hasSingleBean(IamAdminExceptionHandler.class);
                });
    }

    private WebApplicationContextRunner runnerWithDependencies() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IamAdminConfiguration.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
                .withBean(IamSessionTokenService.class, () -> mock(IamSessionTokenService.class))
                .withBean(IamSecurityAuditEventAppender.class,
                        () -> mock(IamSecurityAuditEventAppender.class))
                .withBean(IamSecureIdGenerator.class, IamSecureIdGenerator::new)
                .withBean(Clock.class, Clock::systemUTC);
    }

    private void assertAdminBeansAbsent(AssertableWebApplicationContext context) {
        assertThat(context).hasNotFailed();
        assertThat(context).doesNotHaveBean(IamAdminJdbcRepository.class);
        assertThat(context).doesNotHaveBean(IamAdminReadModelRepository.class);
        assertThat(context).doesNotHaveBean(IamAdminService.class);
        assertThat(context).doesNotHaveBean(IamAdminController.class);
        assertThat(context).doesNotHaveBean(IamAdminExceptionHandler.class);
    }
}
