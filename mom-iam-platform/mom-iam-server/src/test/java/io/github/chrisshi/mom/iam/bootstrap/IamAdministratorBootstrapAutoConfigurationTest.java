package io.github.chrisshi.mom.iam.bootstrap;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserRoleMapper;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Bootstrap 启动开关和生产环境 Fail Fast 自动配置测试。
 *
 * <p>测试只验证上下文装配，不执行 Runner 或数据库写入；真实事务行为由 PostgreSQL 集成测试覆盖。</p>
 */
class IamAdministratorBootstrapAutoConfigurationTest {

    @Test
    void bootstrapMustRemainDisabledByDefault() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ApplicationRunner.class);
        });
    }

    @Test
    void enabledBootstrapMustRejectMissingPasswordDuringContextStartup() {
        runner()
                .withPropertyValues("mom.iam.bootstrap.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodProfileMustRejectBootstrapDuringContextStartup() {
        runner()
                .withInitializer(context ->
                        context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues(
                        "mom.iam.bootstrap.enabled=true",
                        "mom.iam.bootstrap.password=Bootstrap-Temporary-Secret-2026!")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void productionProfileMustRejectBootstrapDuringContextStartup() {
        runner()
                .withInitializer(context ->
                        context.getEnvironment().setActiveProfiles("production"))
                .withPropertyValues(
                        "mom.iam.bootstrap.enabled=true",
                        "mom.iam.bootstrap.password=Bootstrap-Temporary-Secret-2026!")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void safeNonProductionConfigurationMustRegisterSingleRunner() {
        runner()
                .withPropertyValues(
                        "mom.iam.bootstrap.enabled=true",
                        "mom.iam.bootstrap.password=Bootstrap-Temporary-Secret-2026!")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IamBuiltInAdministratorBootstrap.class);
                    assertThat(context).hasSingleBean(ApplicationRunner.class);
                });
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IamAdministratorBootstrapConfiguration.class))
                .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
                .withBean(AuditContextExecutor.class, AuditContextExecutor::new)
                .withBean(IamUserMapper.class, () -> mock(IamUserMapper.class))
                .withBean(IamRoleMapper.class, () -> mock(IamRoleMapper.class))
                .withBean(IamUserRoleMapper.class, () -> mock(IamUserRoleMapper.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
                .withBean(IamSecureIdGenerator.class, IamSecureIdGenerator::new)
                .withBean(Clock.class, Clock::systemUTC);
    }
}
