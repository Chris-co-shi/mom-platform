package io.github.chrisshi.mom.mdm.controller;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * MDM 本地接口文档元数据，属于 HTTP 入站边界。
 *
 * <p>只在 local Profile 注册，不依赖 Application 或持久层；无共享可变状态和事务副作用。
 * local 文档组件启动失败时本地进程直接失败，不伪造契约；Base Profile 仍关闭文档。
 * 文档路径为服务内部路径。</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class MdmOpenApiConfiguration {

    /**
     * 声明 MDM 文档身份，不修改业务接口契约。
     *
     * @return 仅用于本地文档生成的 OpenAPI 元数据
     */
    @Bean
    public OpenAPI mdmOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MOM MDM Service API")
                .version("0.1.0")
                .description("服务内部 Controller 路径；Gateway 路径与鉴权规则见 docs/api/README.md。"));
    }
}
