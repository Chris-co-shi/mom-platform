package io.github.chrisshi.mom.integration.interfaces.rest;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 仅为本机技术探针快照提供稳定的 OpenAPI 身份信息。 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class IntegrationOpenApiConfiguration {
    /** 声明 Integration 服务快照的标题和用途。 */
    @Bean
    public OpenAPI integrationOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MOM Integration Service API")
                .version("0.1.0")
                .description("仅包含本地启用的 Integration 技术探针，不代表正式业务 API。"));
    }
}
