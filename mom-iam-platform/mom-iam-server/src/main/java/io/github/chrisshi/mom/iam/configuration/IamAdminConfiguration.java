package io.github.chrisshi.mom.iam.configuration;

import io.github.chrisshi.mom.iam.application.admin.*;
import io.github.chrisshi.mom.iam.application.admin.port.*;
import io.github.chrisshi.mom.iam.bootstrap.IamAdministratorBootstrapConfiguration;
import io.github.chrisshi.mom.iam.domain.policy.PlatformAdministratorRetentionPolicy;
import io.github.chrisshi.mom.iam.domain.role.IamRoleRepository;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccountRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.*;
import io.github.chrisshi.mom.iam.infrastructure.persistence.query.*;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.*;
import io.github.chrisshi.mom.iam.security.*;
import io.github.chrisshi.mom.iam.web.admin.*;
import io.github.chrisshi.mom.iam.web.admin.audit.IamSecurityAuditController;
import io.github.chrisshi.mom.iam.web.admin.client.IamClientAdminController;
import io.github.chrisshi.mom.iam.web.admin.role.IamRoleAdminController;
import io.github.chrisshi.mom.iam.web.admin.session.IamSessionAdminController;
import io.github.chrisshi.mom.iam.web.admin.user.IamUserAdminController;
import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/** IAM Admin 分层能力装配；只连接 Port、Adapter、Application 与 Web。 */
@AutoConfiguration(after = {
        IamPersistenceRepositoryAutoConfiguration.class,
        IamAuthorizationServerConfiguration.class,
        IamAdministratorBootstrapConfiguration.class
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(SqlSessionFactory.class)
@ConditionalOnProperty(prefix = "mom.iam.admin", name = "enabled", havingValue = "true")
public class IamAdminConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MybatisIamUserAccountRepository mybatisIamUserAccountRepository() {
        return new MybatisIamUserAccountRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    MybatisIamRoleRepository mybatisIamRoleRepository() {
        return new MybatisIamRoleRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    IamPermissionAdminQueryPort iamPermissionAdminQueryPort(IamPermissionMapper mapper) {
        return new MybatisIamPermissionAdminQuery(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    IamAuthorizationAssignmentPort iamAuthorizationAssignmentPort(
            IamUserMapper userMapper,
            IamRoleMapper roleMapper,
            IamUserRoleMapper userRoleMapper,
            IamRolePermissionMapper rolePermissionMapper) {
        return new MybatisIamAuthorizationAssignmentRepository(
                userMapper, roleMapper, userRoleMapper, rolePermissionMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    IamUserAccessPort iamUserAccessPort(
            IamUserFactoryScopeMapper factoryScopeMapper,
            IamUserApplicationMapper applicationMapper,
            IamExternalUserBindingMapper bindingMapper) {
        return new MybatisIamUserAccessRepository(
                factoryScopeMapper, applicationMapper, bindingMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    IamSessionAdminQueryPort iamSessionAdminQueryPort(IamUserSessionMapper mapper) {
        return new MybatisIamSessionAdminQuery(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    IamClientPolicyAdminPort iamClientPolicyAdminPort(IamOauthClientPolicyMapper mapper) {
        return new MybatisIamClientPolicyAdminRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    IamSecurityAuditQueryPort iamSecurityAuditQueryPort(
            IamSecurityAuditEventMapper mapper) {
        return new MybatisIamSecurityAuditQuery(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    IamAuthorizationReadPort iamAuthorizationReadPort(
            IamUserMapper userMapper,
            IamRoleMapper roleMapper,
            IamUserRoleMapper userRoleMapper,
            IamRolePermissionMapper rolePermissionMapper,
            IamUserFactoryScopeMapper factoryScopeMapper,
            IamUserApplicationMapper applicationMapper,
            IamExternalUserBindingMapper bindingMapper) {
        return new IamAuthorizationReadQuery(
                userMapper, roleMapper, userRoleMapper, rolePermissionMapper,
                factoryScopeMapper, applicationMapper, bindingMapper);
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
    IamPasswordHasher iamPasswordHasher(PasswordEncoder encoder) {
        return encoder::encode;
    }

    @Bean
    @ConditionalOnMissingBean
    IamSessionRevocationPort iamSessionRevocationPort(IamSessionTokenService sessions) {
        return new IamSessionRevocationAdapter(sessions);
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformAdministratorRetentionPolicy platformAdministratorRetentionPolicy() {
        return new PlatformAdministratorRetentionPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    IamAdminAuditService iamAdminAuditService(
            IamSecurityAuditSink sink, IamIdentifierGenerator ids, Clock clock) {
        return new IamAdminAuditService(sink, ids, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    IamSessionRevocationService iamSessionRevocationService(
            IamSessionAdminQueryPort queries, IamSessionRevocationPort revocations) {
        return new IamSessionRevocationService(queries, revocations);
    }

    @Bean
    @ConditionalOnMissingBean
    IamPlatformAdministratorGuard iamPlatformAdministratorGuard(
            IamPlatformAdministratorPort administrators,
            PlatformAdministratorRetentionPolicy policy,
            Clock clock) {
        return new IamPlatformAdministratorGuard(administrators, policy, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    IamUserAdminApplicationService iamUserAdminApplicationService(
            IamUserAccountRepository users,
            IamUserAdminQueryPort userQueries,
            IamUserAccessPort access,
            IamPasswordHasher passwordHasher,
            IamIdentifierGenerator ids,
            IamAdminAuditService audits,
            IamSessionRevocationService revocations,
            IamPlatformAdministratorGuard platformAdministrators,
            Clock clock) {
        return new IamUserAdminApplicationService(
                users, userQueries, access, passwordHasher, ids, audits,
                revocations, platformAdministrators, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    IamUserAuthorizationApplicationService iamUserAuthorizationApplicationService(
            IamUserAccountRepository users,
            IamRoleRepository roles,
            IamAuthorizationAssignmentPort assignments,
            IamUserAccessPort access,
            IamAuthorizationReadPort readModels,
            IamExternalFactoryScopeVerifier externalFactoryVerifier,
            IamIdentifierGenerator ids,
            IamAdminAuditService audits,
            IamSessionRevocationService revocations,
            IamPlatformAdministratorGuard platformAdministrators,
            Clock clock) {
        return new IamUserAuthorizationApplicationService(
                users, roles, assignments, access, readModels,
                externalFactoryVerifier, ids, audits, revocations,
                platformAdministrators, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    IamRoleAdminApplicationService iamRoleAdminApplicationService(
            IamRoleRepository roles,
            IamRoleAdminQueryPort roleQueries,
            IamPermissionAdminQueryPort permissionQueries,
            IamAuthorizationAssignmentPort assignments,
            IamAuthorizationReadPort readModels,
            IamIdentifierGenerator ids,
            IamAdminAuditService audits,
            Clock clock) {
        return new IamRoleAdminApplicationService(
                roles, roleQueries, permissionQueries, assignments,
                readModels, ids, audits, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    IamSessionAdminApplicationService iamSessionAdminApplicationService(
            IamSessionAdminQueryPort queries,
            IamUserAccountRepository users,
            IamSessionRevocationService revocations,
            IamAdminAuditService audits) {
        return new IamSessionAdminApplicationService(
                queries, users, revocations, audits);
    }

    @Bean
    @ConditionalOnMissingBean
    IamClientAdminApplicationService iamClientAdminApplicationService(
            IamClientPolicyAdminPort clients,
            IamSessionRevocationService revocations,
            IamAdminAuditService audits,
            Clock clock) {
        return new IamClientAdminApplicationService(
                clients, revocations, audits, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    IamSecurityAuditQueryService iamSecurityAuditQueryService(
            IamSecurityAuditQueryPort queries) {
        return new IamSecurityAuditQueryService(queries);
    }

    @Bean
    @ConditionalOnMissingBean
    IamAdminWebSupport iamAdminWebSupport(MomAuthorizationService authorization) {
        return new IamAdminWebSupport(authorization);
    }

    @Bean
    @ConditionalOnMissingBean
    IamUserAdminController iamUserAdminController(
            IamUserAdminApplicationService users,
            IamUserAuthorizationApplicationService authorizations,
            IamAdminWebSupport web) {
        return new IamUserAdminController(users, authorizations, web);
    }

    @Bean
    @ConditionalOnMissingBean
    IamRoleAdminController iamRoleAdminController(
            IamRoleAdminApplicationService roles, IamAdminWebSupport web) {
        return new IamRoleAdminController(roles, web);
    }

    @Bean
    @ConditionalOnMissingBean
    IamSessionAdminController iamSessionAdminController(
            IamSessionAdminApplicationService sessions, IamAdminWebSupport web) {
        return new IamSessionAdminController(sessions, web);
    }

    @Bean
    @ConditionalOnMissingBean
    IamClientAdminController iamClientAdminController(
            IamClientAdminApplicationService clients, IamAdminWebSupport web) {
        return new IamClientAdminController(clients, web);
    }

    @Bean
    @ConditionalOnMissingBean
    IamSecurityAuditController iamSecurityAuditController(
            IamSecurityAuditQueryService audits, IamAdminWebSupport web) {
        return new IamSecurityAuditController(audits, web);
    }

    @Bean
    @ConditionalOnMissingBean
    IamAdminExceptionHandler iamAdminExceptionHandler() {
        return new IamAdminExceptionHandler();
    }
}
