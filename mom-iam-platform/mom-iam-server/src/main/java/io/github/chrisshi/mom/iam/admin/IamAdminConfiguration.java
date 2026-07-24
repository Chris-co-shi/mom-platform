package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.autoconfigure.IamPersistenceRepositoryAutoConfiguration;
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
        IamAuthorizationServerConfiguration.class
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

    /** 注册管理端应用事务服务。 */
    @Bean
    @ConditionalOnMissingBean
    IamAdminService iamAdminService(
            IamUserAdminRepository users,
            IamRoleAdminRepository roles,
            IamAuthorizationAssignmentRepository assignments,
            IamUserAccessAdminRepository access,
            IamSessionAdminRepository sessionQueries,
            IamClientPolicyAdminRepository clients,
            IamSecurityAuditQueryRepository auditQueries,
            IamAdminReadModelRepository readModels,
            MomAuthorizationService authorization,
            PasswordEncoder passwordEncoder,
            IamSessionTokenService sessions,
            IamSecurityAuditEventAppender auditEvents,
            IamExternalFactoryScopeVerifier externalFactoryVerifier,
            IamSecureIdGenerator ids,
            Clock clock) {
        return new IamAdminService(
                users, roles, assignments, access, sessionQueries, clients, auditQueries,
                readModels, authorization, passwordEncoder, sessions, auditEvents,
                externalFactoryVerifier, ids, clock);
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
