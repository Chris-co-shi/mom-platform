package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.autoconfigure.IamPersistenceRepositoryAutoConfiguration;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSecurityAuditEventAppender;
import io.github.chrisshi.mom.iam.security.IamAuthorizationServerConfiguration;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/** S07 IAM 管理 API Bean 组装；只复用已冻结 Schema、认证和 Session 权威服务。 */
@AutoConfiguration(after = {
        IamPersistenceRepositoryAutoConfiguration.class,
        IamAuthorizationServerConfiguration.class
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "mom.iam.admin",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IamAdminConfiguration {

    @Bean
    @ConditionalOnMissingBean
    IamAdminJdbcRepository iamAdminJdbcRepository(JdbcTemplate jdbcTemplate) {
        return new IamAdminJdbcRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    MomAuthorizationService momAuthorizationService() {
        return new MomAuthorizationService();
    }

    @Bean
    @ConditionalOnMissingBean
    IamExternalFactoryScopeVerifier iamExternalFactoryScopeVerifier() {
        return IamExternalFactoryScopeVerifier.failClosed();
    }

    @Bean
    @ConditionalOnMissingBean
    IamAdminService iamAdminService(
            IamAdminJdbcRepository repository,
            MomAuthorizationService authorization,
            PasswordEncoder passwordEncoder,
            IamSessionTokenService sessions,
            IamSecurityAuditEventAppender auditEvents,
            IamExternalFactoryScopeVerifier externalFactoryVerifier,
            IamSecureIdGenerator ids,
            Clock clock) {
        return new IamAdminService(
                repository, authorization, passwordEncoder, sessions, auditEvents,
                externalFactoryVerifier, ids, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    IamAdminController iamAdminController(IamAdminService service) {
        return new IamAdminController(service);
    }

    @Bean
    @ConditionalOnMissingBean
    IamAdminExceptionHandler iamAdminExceptionHandler() {
        return new IamAdminExceptionHandler();
    }
}
