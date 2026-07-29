package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamUserRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import javax.sql.DataSource;

/**
 * IAM Authorization Server 的条件入口和内聚配置聚合器。
 *
 * <p>该类只保留自动配置条件、属性注册和配置模块导入，不再承载协议、登录、Token 或 Session 业务。
 * 拆分后的配置仍共享原有条件、Bean 名称、FilterChain 顺序和唯一签名基础设施。</p>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
        "io.github.chrisshi.mom.iam.autoconfigure.IamPersistenceRepositoryAutoConfiguration"
})
@EnableWebSecurity
@EnableConfigurationProperties({IamAuthorizationProperties.class, IamSessionProperties.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean({DataSource.class, IamUserRepository.class})
@ConditionalOnProperty(
        prefix = "mom.iam.authorization",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@Import({
        IamAuthenticationCoreConfiguration.class,
        IamTokenInfrastructureConfiguration.class,
        IamAuthorizationServerProtocolConfiguration.class,
        IamLoginPageSecurityConfiguration.class
})
public class IamAuthorizationServerConfiguration {
}
