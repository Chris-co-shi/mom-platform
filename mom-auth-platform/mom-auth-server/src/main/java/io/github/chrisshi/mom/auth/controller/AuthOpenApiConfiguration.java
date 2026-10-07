package io.github.chrisshi.mom.auth.controller;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Auth 本地接口文档元数据，属于 HTTP 入站边界。
 *
 * <p>只在 local Profile 注册，不依赖 Application 或持久层；无共享可变状态和事务副作用。
 * local 文档组件启动失败时本地进程直接失败，不伪造契约；Base Profile 仍关闭文档。
 * 文档路径为服务内部路径，Gateway 路径另行映射。</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class AuthOpenApiConfiguration {

    /**
     * 声明 Auth 文档身份，不对业务接口添加或放宽安全规则。
     *
     * @return 仅用于本地文档生成的 OpenAPI 元数据
     */
    @Bean
    public OpenAPI authOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MOM Auth Service API")
                .version("0.1.0")
                .description("服务内部 Controller 路径；Gateway 路径与鉴权规则见 docs/api/README.md。"));
    }
}
