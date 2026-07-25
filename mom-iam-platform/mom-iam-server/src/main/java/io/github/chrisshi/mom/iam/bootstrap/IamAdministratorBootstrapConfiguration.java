package io.github.chrisshi.mom.iam.bootstrap;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.autoconfigure.IamPersistenceRepositoryAutoConfiguration;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserRoleMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamBuiltInAdministratorRepository;
import io.github.chrisshi.mom.iam.security.IamAuthorizationServerConfiguration;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import org.apache.ibatis.session.SqlSessionFactory;
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

/**
 * IAM 内置管理员 Bootstrap 自动配置。
 *
 * <p>仓储和事务服务复用 IAM 唯一 DataSource、MyBatis 与 Spring 事务管理器。只有
 * {@code mom.iam.bootstrap.enabled=true} 才注册启动 Runner；启用但缺少认证密码编码器或 ID 生成器
 * 时上下文直接失败，不会静默跳过初始化。</p>
 */
@AutoConfiguration(after = {
        IamPersistenceRepositoryAutoConfiguration.class,
        IamAuthorizationServerConfiguration.class
})
@ConditionalOnBean(SqlSessionFactory.class)
@EnableConfigurationProperties(IamAdministratorBootstrapProperties.class)
public class IamAdministratorBootstrapConfiguration {

    /** 注册不读取密码摘要的内置管理员精确仓储。 */
    @Bean
    @ConditionalOnMissingBean
    IamBuiltInAdministratorRepository iamBuiltInAdministratorRepository(
            IamUserMapper userMapper,
            IamRoleMapper roleMapper,
            IamUserRoleMapper userRoleMapper) {
        return new IamBuiltInAdministratorRepository(userMapper, roleMapper, userRoleMapper);
    }

    /** 在认证基础设施可用时注册 Bootstrap 事务服务。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PasswordEncoder.class, IamSecureIdGenerator.class})
    IamBuiltInAdministratorBootstrap iamBuiltInAdministratorBootstrap(
            IamBuiltInAdministratorRepository repository,
            AuditContextExecutor auditContextExecutor,
            IamAdministratorBootstrapProperties properties,
            PasswordEncoder passwordEncoder,
            IamSecureIdGenerator ids,
            Environment environment,
            Clock clock) {
        return new IamBuiltInAdministratorBootstrap(
                repository, auditContextExecutor, properties, passwordEncoder, ids, environment, clock);
    }

    /**
     * 注册一次性启动 Runner。
     *
     * <p>Bean 创建阶段先拒绝生产 Profile 和缺失密码，Runner 执行阶段再在单个事务中完成数据库操作。</p>
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "mom.iam.bootstrap",
            name = "enabled",
            havingValue = "true")
    ApplicationRunner iamBuiltInAdministratorBootstrapRunner(
            IamBuiltInAdministratorBootstrap bootstrap,
            IamAdministratorBootstrapProperties properties,
            Environment environment) {
        properties.validate(environment);
        return arguments -> bootstrap.initialize();
    }
}
