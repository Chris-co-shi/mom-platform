package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/** S09 MOM Admin 只读授权投影自动配置。 */
@AutoConfiguration(after = IamAdminConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean({JdbcTemplate.class, IamAdminService.class, MomAuthorizationService.class})
@ConditionalOnProperty(prefix = "mom.iam.admin", name = "enabled", havingValue = "true")
public class IamAdminReadModelConfiguration {

    @Bean
    IamAdminReadModelRepository iamAdminReadModelRepository(JdbcTemplate jdbcTemplate) {
        return new IamAdminReadModelRepository(jdbcTemplate);
    }

    @Bean
    IamAdminReadModelController iamAdminReadModelController(
            IamAdminReadModelRepository repository,
            MomAuthorizationService authorization) {
        return new IamAdminReadModelController(repository, authorization);
    }

    @Bean
    IamAdminReadModelExceptionHandler iamAdminReadModelExceptionHandler() {
        return new IamAdminReadModelExceptionHandler();
    }
}
