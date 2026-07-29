package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.autoconfigure.IamPersistenceRepositoryAutoConfiguration;
import io.github.chrisshi.mom.iam.bootstrap.IamAdministratorBootstrapConfiguration;
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
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamBuiltInAdministratorRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSecurityAuditEventAppender;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamAuthorizationAssignmentRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamClientPolicyAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamRoleAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamSecurityAuditQueryRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamSessionAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamUserAccessAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamUserAdminRepository;
import io.github.chrisshi.mom.iam.security.IamAuthorizationServerConfiguration;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/**
 * IAM 管理 API 自动配置。
 *
 * <p>管理能力必须由 {@code mom.iam.admin.enabled=true} 显式开启，并且只有 MyBatis
 * {@link SqlSessionFactory} 可用时才注册。配置直接组装明确用途 Repository、应用服务和 Controller，
 * 不再以 Spring JDBC 模板作为 MOM 业务持久化前提；Spring Authorization Server 官方 JDBC Store
 * 仍由协议配置独立管理。</p>
 */
@AutoConfiguration(after = {
        IamPersistenceRepositoryAutoConfiguration.class,
        IamAuthorizationServerConfiguration.class,
        IamAdministratorBootstrapConfiguration.class
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(SqlSessionFactory.class)
@ConditionalOnProperty(prefix = "mom.iam.admin", name = "enabled", havingValue = "true")
public class IamAdminConfiguration {

    /** 注册用户管理仓储。 */
    @Bean
    @ConditionalOnMissingBean
    IamUserAdminRepository iamUserAdminRepository(IamUserMapper mapper) {
        return new IamUserAdminRepository(mapper);
    }

    /** 注册角色与 Permission 目录管理仓储。 */
    @Bean
    @ConditionalOnMissingBean
    IamRoleAdminRepository iamRoleAdminRepository(
            IamRoleMapper roleMapper, IamPermissionMapper permissionMapper) {
        return new IamRoleAdminRepository(roleMapper, permissionMapper);
    }

    /** 注册用户角色与角色 Permission 全量替换仓储。 */
    @Bean
    @ConditionalOnMissingBean
    IamAuthorizationAssignmentRepository iamAuthorizationAssignmentRepository(
            IamUserMapper userMapper,
            IamRoleMapper roleMapper,
            IamUserRoleMapper userRoleMapper,
            IamRolePermissionMapper rolePermissionMapper) {
        return new IamAuthorizationAssignmentRepository(
                userMapper, roleMapper, userRoleMapper, rolePermissionMapper);
    }

    /** 注册 Factory、Mobile 与 Party 管理仓储。 */
    @Bean
    @ConditionalOnMissingBean
    IamUserAccessAdminRepository iamUserAccessAdminRepository(
            IamUserFactoryScopeMapper factoryScopeMapper,
            IamUserApplicationMapper applicationMapper,
            IamExternalUserBindingMapper bindingMapper) {
        return new IamUserAccessAdminRepository(
                factoryScopeMapper, applicationMapper, bindingMapper);
    }

    /** 注册 Session 管理查询仓储。 */
    @Bean
    @ConditionalOnMissingBean
    IamSessionAdminRepository iamSessionAdminRepository(IamUserSessionMapper mapper) {
        return new IamSessionAdminRepository(mapper);
    }

    /** 注册 Client Policy 管理仓储。 */
    @Bean
    @ConditionalOnMissingBean
    IamClientPolicyAdminRepository iamClientPolicyAdminRepository(
            IamOauthClientPolicyMapper mapper) {
        return new IamClientPolicyAdminRepository(mapper);
    }

    /** 注册安全审计只读管理仓储。 */
    @Bean
    @ConditionalOnMissingBean
    IamSecurityAuditQueryRepository iamSecurityAuditQueryRepository(
            IamSecurityAuditEventMapper mapper) {
        return new IamSecurityAuditQueryRepository(mapper);
    }

    /** 注册用户授权与角色 Permission 聚合查询仓储。 */
    @Bean
    @ConditionalOnMissingBean
    IamAdminReadModelRepository iamAdminReadModelRepository(
            IamUserMapper userMapper,
            IamRoleMapper roleMapper,
            IamUserRoleMapper userRoleMapper,
            IamRolePermissionMapper rolePermissionMapper,
            IamUserFactoryScopeMapper factoryScopeMapper,
            IamUserApplicationMapper applicationMapper,
            IamExternalUserBindingMapper bindingMapper) {
        return new IamAdminReadModelRepository(
                userMapper, roleMapper, userRoleMapper, rolePermissionMapper,
                factoryScopeMapper, applicationMapper, bindingMapper);
    }

    /** 注册统一的管理端 Permission 判定服务。 */
    @Bean
    @ConditionalOnMissingBean
    MomAuthorizationService momAuthorizationService() {
        return new MomAuthorizationService();
    }

    /** 默认外部 Factory 校验 Fail Closed，由正式 MDM Adapter 覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    IamExternalFactoryScopeVerifier iamExternalFactoryScopeVerifier() {
        return IamExternalFactoryScopeVerifier.failClosed();
    }

    /** 组装管理用例共享的安全守卫、Session 撤销和审计支撑。 */
    @Bean
    @ConditionalOnMissingBean
    IamAdminOperationSupport iamAdminOperationSupport(
            IamUserAdminRepository users,
            IamSessionAdminRepository sessionQueries,
            IamBuiltInAdministratorRepository builtInAdministrators,
            MomAuthorizationService authorization,
            IamSessionTokenService sessions,
            IamSecurityAuditEventAppender auditEvents,
            IamSecureIdGenerator ids,
            Clock clock) {
        return new IamAdminOperationSupport(
                users, sessionQueries, builtInAdministrators, authorization,
                sessions, auditEvents, ids, clock);
    }

    /** 注册用户管理应用服务。 */
    @Bean
    @ConditionalOnMissingBean
    IamUserAdminApplicationService iamUserAdminApplicationService(
            IamUserAdminRepository users,
            IamUserAccessAdminRepository access,
            PasswordEncoder passwordEncoder,
            IamAdminOperationSupport support) {
        return new IamUserAdminApplicationService(users, access, passwordEncoder, support);
    }

    /** 注册用户授权关系应用服务。 */
    @Bean
    @ConditionalOnMissingBean
    IamUserAuthorizationApplicationService iamUserAuthorizationApplicationService(
            IamRoleAdminRepository roles,
            IamAuthorizationAssignmentRepository assignments,
            IamUserAccessAdminRepository access,
            IamAdminReadModelRepository readModels,
            IamExternalFactoryScopeVerifier externalFactoryVerifier,
            IamAdminOperationSupport support) {
        return new IamUserAuthorizationApplicationService(
                roles, assignments, access, readModels, externalFactoryVerifier, support);
    }

    /** 注册角色和 Permission 管理应用服务。 */
    @Bean
    @ConditionalOnMissingBean
    IamRoleAdminApplicationService iamRoleAdminApplicationService(
            IamRoleAdminRepository roles,
            IamAuthorizationAssignmentRepository assignments,
            IamAdminReadModelRepository readModels,
            IamAdminOperationSupport support) {
        return new IamRoleAdminApplicationService(roles, assignments, readModels, support);
    }

    /** 注册 Session 管理应用服务。 */
    @Bean
    @ConditionalOnMissingBean
    IamSessionAdminApplicationService iamSessionAdminApplicationService(
            IamSessionAdminRepository sessionQueries,
            IamAdminOperationSupport support) {
        return new IamSessionAdminApplicationService(sessionQueries, support);
    }

    /** 注册 Client Policy 管理应用服务。 */
    @Bean
    @ConditionalOnMissingBean
    IamClientAdminApplicationService iamClientAdminApplicationService(
            IamClientPolicyAdminRepository clients,
            IamSessionAdminRepository sessionQueries,
            IamAdminOperationSupport support) {
        return new IamClientAdminApplicationService(clients, sessionQueries, support);
    }

    /** 注册追加型安全审计查询服务。 */
    @Bean
    @ConditionalOnMissingBean
    IamSecurityAuditQueryService iamSecurityAuditQueryService(
            IamSecurityAuditQueryRepository auditQueries,
            IamAdminOperationSupport support) {
        return new IamSecurityAuditQueryService(auditQueries, support);
    }

    /** 注册 Controller 继续依赖的兼容 Facade。 */
    @Bean
    @ConditionalOnMissingBean
    IamAdminService iamAdminService(
            IamUserAdminApplicationService users,
            IamUserAuthorizationApplicationService userAuthorizations,
            IamRoleAdminApplicationService roles,
            IamSessionAdminApplicationService sessions,
            IamClientAdminApplicationService clients,
            IamSecurityAuditQueryService audits) {
        return new IamAdminService(users, userAuthorizations, roles, sessions, clients, audits);
    }

    /** 注册管理 REST Controller。 */
    @Bean
    @ConditionalOnMissingBean
    IamAdminController iamAdminController(IamAdminService service) {
        return new IamAdminController(service);
    }

    /** 注册稳定错误码与 HTTP 状态映射。 */
    @Bean
    @ConditionalOnMissingBean
    IamAdminExceptionHandler iamAdminExceptionHandler() {
        return new IamAdminExceptionHandler();
    }
}
