package io.github.chrisshi.mom.gateway;

import io.github.chrisshi.mom.gateway.filter.CorrelationIdGlobalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MomGatewayApplicationTest {

    @Test
    void gatewayStartsWithDiscoveryRouteAndCorrelationFilter() {
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
            assertNotNull(context.getBean(CorrelationIdGlobalFilter.class));

            RouteDefinitionLocator locator = context.getBean(RouteDefinitionLocator.class);
            List<RouteDefinition> routes = locator.getRouteDefinitions()
                    .collectList()
                    .block(Duration.ofSeconds(5));
            assertNotNull(routes);

            RouteDefinition integrationRoute = routes.stream()
                    .filter(route -> "integration-service".equals(route.getId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(URI.create("lb://mom-integration-server"), integrationRoute.getUri());
        }
    }
}
