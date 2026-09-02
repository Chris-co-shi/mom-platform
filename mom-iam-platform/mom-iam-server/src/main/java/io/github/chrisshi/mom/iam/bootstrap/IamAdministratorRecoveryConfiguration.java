package io.github.chrisshi.mom.iam.bootstrap;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.application.admin.IamSessionRevocationService;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditSink;
import io.github.chrisshi.mom.iam.configuration.IamAdminConfiguration;
import io.github.chrisshi.mom.iam.configuration.IamPersistenceRepositoryAutoConfiguration;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamBuiltInAdministratorRepository;
import io.github.chrisshi.mom.iam.security.IamAuthorizationServerConfiguration;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/** IAM 内置管理员一次性本地恢复自动配置。 */
@AutoConfiguration(after = {
        IamPersistenceRepositoryAutoConfiguration.class,
        IamAuthorizationServerConfiguration.class,
        IamAdministratorBootstrapConfiguration.class,
        IamAdminConfiguration.class
})
@ConditionalOnBean(SqlSessionFactory.class)
@EnableConfigurationProperties(IamAdministratorRecoveryProperties.class)
public class IamAdministratorRecoveryConfiguration {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(IamAdministratorRecoveryConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    IamBuiltInAdministratorRecovery iamBuiltInAdministratorRecovery(
            IamBuiltInAdministratorRepository repository,
            AuditContextExecutor auditContextExecutor,
            IamAdministratorRecoveryProperties properties,
            PasswordEncoder passwordEncoder,
            IamSecurityAuditSink auditSink,
            IamSecureIdGenerator ids,
            Environment environment,
            Clock clock) {
        return new IamBuiltInAdministratorRecovery(
                repository, auditContextExecutor, properties,
                passwordEncoder, auditSink, ids, environment, clock);
    }

    /**
     * 注册一次性恢复 Runner。
     *
     * <p>凭证恢复与安全审计事务提交后再撤销 Session，避免 Redis 调用进入 PostgreSQL 本地事务。Bootstrap 与
     * Recovery 禁止同时启用；恢复完成后必须关闭开关并移除密码环境变量。</p>
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "mom.iam.recovery",
            name = "enabled",
            havingValue = "true")
    ApplicationRunner iamBuiltInAdministratorRecoveryRunner(
            IamBuiltInAdministratorRecovery recovery,
            IamAdministratorRecoveryProperties properties,
            IamAdministratorBootstrapProperties bootstrapProperties,
            Environment environment,
            ObjectProvider<IamSessionRevocationService> revocationProvider) {
        properties.validate(environment);
        if (bootstrapProperties.isEnabled()) {
            throw new IllegalStateException(
                    "IAM bootstrap and administrator recovery cannot be enabled together");
        }
        IamSessionRevocationService revocations = revocationProvider.getIfAvailable();
        if (revocations == null) {
            throw new IllegalStateException(
                    "IAM administrator recovery requires IAM admin/session revocation infrastructure");
        }
        return arguments -> {
            IamBuiltInAdministratorRecovery.RecoveryResult result = recovery.recoverCredential();
            int revokedSessions = revocations.revokeUserSessions(
                    result.userId(),
                    IamBuiltInAdministratorRecovery.SYSTEM_ACTOR,
                    "administrator_recovery");
            LOGGER.info(
                    "IAM built-in administrator credential recovered: username=admin, sessionsRevoked={}, forcePasswordChange={}",
                    revokedSessions, result.forcePasswordChange());
        };
    }
}
