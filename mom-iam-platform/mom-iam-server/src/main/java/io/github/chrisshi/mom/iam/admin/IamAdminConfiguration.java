package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.autoconfigure.IamPersistenceRepositoryAutoConfiguration;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSecurityAuditEventAppender;
import io.github.chrisshi.mom.iam.security.IamAuthorizationServerConfiguration;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/**
 * S07 IAM 管理 API 自动配置。
 *
 * <p>管理能力必须由 {@code mom.iam.admin.enabled=true} 显式开启，并且只有 Servlet IAM 应用已经创建
 * {@link JdbcTemplate} 后才整体生效。配置顺序固定在 Spring JDBC、IAM 持久化仓储和 Authorization
 * Server 之后；Repository、Service、Controller 在同一配置中直接组装，不使用互相依赖的条件 Bean 链。</p>
 */
@AutoConfiguration(after = {
        JdbcTemplateAutoConfiguration.class,
        IamPersistenceRepositoryAutoConfiguration.class,
        IamAuthorizationServerConfiguration.class
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(
        prefix = "mom.iam.admin",
        name = "enabled",
        havingValue = "true")
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
