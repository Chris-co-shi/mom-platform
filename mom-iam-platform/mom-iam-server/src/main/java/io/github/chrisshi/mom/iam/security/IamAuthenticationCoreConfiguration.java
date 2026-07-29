package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.application.authentication.IamFirstPartyLoginApplicationService;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamAuthorizationCatalogRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamAuthorizationContextRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamIdentityBindingRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSessionRefreshRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamUserAccessRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/**
 * IAM 账号认证、授权上下文、Session 和第一方登录用例的 Bean 装配边界。
 *
 * <p>本配置只连接既有应用服务与基础设施，不承载业务流程；数据库或 Redis 不可用时沿用下游服务的
 * 失败关闭语义。所有 Bean 名称、Primary 选择和安全配置校验保持 S08 前行为。</p>
 */
@Configuration(proxyBeanMethods = false)
class IamAuthenticationCoreConfiguration {

    @Bean
    PasswordEncoder iamPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    IamAccountAuthenticationService iamAccountAuthenticationService(
            IamUserRepository users,
            PasswordEncoder passwordEncoder,
            IamAuthorizationProperties properties,
            Clock clock) {
        properties.validate();
        return new IamAccountAuthenticationService(users, passwordEncoder, properties, clock);
    }

    @Bean
    @Primary
    AuthenticationProvider iamAuthenticationProvider(
            IamAccountAuthenticationService accounts,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    IamClientAccessPolicyService iamClientAccessPolicyService(
            IamAccountAuthenticationService accounts,
            IamAuthorizationCatalogRepository catalog,
            IamIdentityBindingRepository bindings,
            IamUserAccessRepository accessRepository,
            Clock clock) {
        return new IamClientAccessPolicyService(accounts, catalog, bindings, accessRepository, clock);
    }

    @Bean({"iamAuthorizationContextLoader", "iamAuthorizationContextService"})
    IamAuthorizationContextLoader iamAuthorizationContextLoader(
            IamUserRepository users,
            IamAuthorizationContextRepository contexts,
            Clock clock) {
        return new IamAuthorizationContextLoader(users, contexts, clock);
    }

    @Bean
    IamScopeGuard iamScopeGuard() {
        return new IamScopeGuard();
    }

    @Bean
    IamRefreshTokenCodec iamRefreshTokenCodec(IamSessionProperties properties) {
        return new IamRefreshTokenCodec(properties);
    }

    @Bean
    IamSecureIdGenerator iamSecureIdGenerator() {
        return new IamSecureIdGenerator();
    }

    @Bean
    IamRevokedSessionStore iamRevokedSessionStore(
            StringRedisTemplate redis,
            IamSessionProperties properties,
            Clock clock) {
        return new IamRevokedSessionStore(redis, properties, clock);
    }

    @Bean
    IamSessionTokenService iamSessionTokenService(
            IamAuthorizationContextLoader contexts,
            IamAuthorizationCatalogRepository catalog,
            IamSessionRefreshRepository repository,
            IamRefreshTokenCodec codec,
            IamSecureIdGenerator ids,
            IamRevokedSessionStore revokedSessions,
            IamSessionProperties properties,
            Environment environment,
            Clock clock) {
        properties.validate(environment.acceptsProfiles(Profiles.of("prod", "production")));
        return new IamSessionTokenService(
                contexts, catalog, repository, codec, ids, revokedSessions, properties, clock);
    }

    @Bean
    IamFirstPartyLoginApplicationService iamFirstPartyLoginApplicationService(
            AuthenticationProvider authenticationProvider,
            IamAccountAuthenticationService accounts,
            IamClientAccessPolicyService clientAccess,
            IamSessionTokenService sessions,
            IamAccessTokenIssuer tokenIssuer,
            AuditContextExecutor auditContextExecutor) {
        return new IamFirstPartyLoginApplicationService(
                authenticationProvider,
                accounts,
                clientAccess,
                sessions,
                tokenIssuer,
                auditContextExecutor);
    }
}
