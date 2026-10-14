package io.github.chrisshi.mom.integration;

import io.github.chrisshi.mom.integration.interfaces.rest.IntegrationIdempotencyProbeController;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 锁定 Integration 探针资源根及 OpenAPI 仅本机启用的配置。 */
class IntegrationRouteContractTest {
    @Test
    void probeMustOwnItsRootAndDocsMustBeLocalOnly() throws Exception {
        assertThat(IntegrationIdempotencyProbeController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/idempotency-probe");
        assertThat(IntegrationIdempotencyProbeController.class.getMethod("acquire", String.class)
                .getAnnotation(PostMapping.class).value()).isEmpty();

        String base = new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);
        String local = new ClassPathResource("application-local.yml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(base).contains("springdoc:", "enabled: false");
        assertThat(local).contains("address: 127.0.0.1", "enabled: true", "/v3/api-docs/**");
    }
}
