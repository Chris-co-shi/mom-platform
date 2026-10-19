package io.github.chrisshi.mom.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 锁定模块前缀、登录独立限流路径及 Gateway 剥除规则。 */
class GatewayModuleRouteContractTest {
    @Test
    void moduleRoutesMustStripOnePrefixAndMustNotExposeApiAliases() throws Exception {
        String yaml = new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);

        assertThat(yaml).contains("Path=/auth/sessions/login", "Path=/auth/**", "Path=/system/**",
                "Path=/mdm/**", "Path=/wms/**", "Path=/qms/**", "Path=/integration/**");
        assertThat(yaml.split("StripPrefix=1", -1)).hasSizeGreaterThanOrEqualTo(6);
        assertThat(yaml).doesNotContain("Path=/auth/login", "Path=/api/integration/**", "PrefixPath=/api");
    }
}
