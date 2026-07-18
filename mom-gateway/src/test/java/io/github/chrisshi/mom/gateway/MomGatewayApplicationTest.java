package io.github.chrisshi.mom.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MomGatewayApplicationTest {

    @Test
    void gatewayStartsAsReactiveApplicationWithoutExternalInfrastructure() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MomGatewayApplication.class)
                .web(WebApplicationType.REACTIVE)
                .properties(
                        "server.port=0",
                        "spring.main.banner-mode=off",
                        "spring.cloud.nacos.discovery.enabled=false",
                        "spring.cloud.nacos.config.enabled=false",
                        "management.endpoints.enabled-by-default=true")
                .run()) {
            assertTrue(context.isActive());
            assertNotNull(context.getBean(HealthEndpoint.class));
        }
    }
}
