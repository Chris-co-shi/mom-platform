package io.github.chrisshi.mom.iam.configuration;

import io.github.chrisshi.mom.iam.application.permissionreference.IamPermissionReferenceApplicationService;
import io.github.chrisshi.mom.iam.application.permissionreference.port.IamPermissionReferenceQueryPort;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamPermissionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.query.MybatisIamPermissionReferenceQuery;
import io.github.chrisshi.mom.iam.security.IamAuthorizationServerConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * IAM Permission Reference 服务间只读能力装配。
 *
 * <p>配置只连接 Application Port 与 MyBatis-Plus Query Adapter，不复用 Admin 分页接口，也不暴露 Permission
 * Entity、Mapper 或数据库 ID。</p>
 */
@AutoConfiguration(after = {
        IamPersistenceRepositoryAutoConfiguration.class,
        IamAuthorizationServerConfiguration.class
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(SqlSessionFactory.class)
@ConditionalOnProperty(
        prefix = "mom.iam.authorization",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IamPermissionReferenceConfiguration {

    @Bean
    @ConditionalOnMissingBean
    IamPermissionReferenceQueryPort iamPermissionReferenceQueryPort(IamPermissionMapper mapper) {
        return new MybatisIamPermissionReferenceQuery(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    IamPermissionReferenceApplicationService iamPermissionReferenceApplicationService(
            IamPermissionReferenceQueryPort queries,
            Clock clock) {
        return new IamPermissionReferenceApplicationService(queries, clock);
    }
}
