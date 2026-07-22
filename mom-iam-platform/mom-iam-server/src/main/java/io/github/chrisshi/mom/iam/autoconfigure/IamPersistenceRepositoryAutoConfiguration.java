package io.github.chrisshi.mom.iam.autoconfigure;

import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamExternalUserBindingMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamInternalUserProfileMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamOauthClientPolicyMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamPermissionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRefreshTokenMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRolePermissionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamSecurityAuditEventMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserApplicationMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserFactoryScopeMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserSessionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamAuthorizationCatalogRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamAuthorizationContextRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamIdentityBindingRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamRefreshTokenStateRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamRoleAssignmentRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSecurityAuditEventAppender;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSessionRefreshJdbcRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSessionStateRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamUserAccessRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamUserRepository;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * IAM 持久化仓储条件自动配置。
 *
 * <p>只有 MyBatis-Plus 已创建 {@link SqlSessionFactory} 时才注册 Repository。无数据库 Bootstrap 明确
 * 排除 DataSource/MyBatis 后不会创建依赖 Mapper 的半成品 Bean；真实 PostgreSQL 模式下 Mapper 由
 * MyBatis 扫描并作为构造参数注入。该配置不创建第二 DataSource 或事务管理器。</p>
 */
@AutoConfiguration(afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnBean(SqlSessionFactory.class)
public class IamPersistenceRepositoryAutoConfiguration {

    @Bean @ConditionalOnMissingBean
    IamUserRepository iamUserRepository(IamUserMapper mapper, JdbcTemplate jdbcTemplate) {
        return new IamUserRepository(mapper, jdbcTemplate);
    }

    @Bean @ConditionalOnMissingBean
    IamAuthorizationCatalogRepository iamAuthorizationCatalogRepository(
            IamRoleMapper roleMapper,
            IamPermissionMapper permissionMapper,
            IamRolePermissionMapper rolePermissionMapper,
            IamOauthClientPolicyMapper clientPolicyMapper) {
        return new IamAuthorizationCatalogRepository(
                roleMapper, permissionMapper, rolePermissionMapper, clientPolicyMapper);
    }

    @Bean @ConditionalOnMissingBean
    IamAuthorizationContextRepository iamAuthorizationContextRepository(JdbcTemplate jdbcTemplate) {
        return new IamAuthorizationContextRepository(jdbcTemplate);
    }

    @Bean @ConditionalOnMissingBean
    IamSessionRefreshJdbcRepository iamSessionRefreshJdbcRepository(JdbcTemplate jdbcTemplate) {
        return new IamSessionRefreshJdbcRepository(jdbcTemplate);
    }

    @Bean @ConditionalOnMissingBean
    IamIdentityBindingRepository iamIdentityBindingRepository(
            IamInternalUserProfileMapper internalProfileMapper,
            IamExternalUserBindingMapper externalBindingMapper) {
        return new IamIdentityBindingRepository(internalProfileMapper, externalBindingMapper);
    }

    @Bean @ConditionalOnMissingBean
    IamRoleAssignmentRepository iamRoleAssignmentRepository(
            IamUserRoleMapper userRoleMapper,
            IamRolePermissionMapper rolePermissionMapper) {
        return new IamRoleAssignmentRepository(userRoleMapper, rolePermissionMapper);
    }

    @Bean @ConditionalOnMissingBean
    IamUserAccessRepository iamUserAccessRepository(
            IamUserApplicationMapper applicationMapper,
            IamUserFactoryScopeMapper factoryScopeMapper) {
        return new IamUserAccessRepository(applicationMapper, factoryScopeMapper);
    }

    @Bean @ConditionalOnMissingBean
    IamSessionStateRepository iamSessionStateRepository(IamUserSessionMapper mapper) {
        return new IamSessionStateRepository(mapper);
    }

    @Bean @ConditionalOnMissingBean
    IamRefreshTokenStateRepository iamRefreshTokenStateRepository(IamRefreshTokenMapper mapper) {
        return new IamRefreshTokenStateRepository(mapper);
    }

    @Bean @ConditionalOnMissingBean
    IamSecurityAuditEventAppender iamSecurityAuditEventAppender(IamSecurityAuditEventMapper mapper) {
        return new IamSecurityAuditEventAppender(mapper);
    }
}
