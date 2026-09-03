package io.github.chrisshi.mom.gateway;

import io.github.chrisshi.mom.gateway.filter.BearerTokenGatewayWebFilter;
import io.github.chrisshi.mom.gateway.filter.CorrelationIdGlobalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gateway 本地启动、路由、关联标识和 Bearer 边缘检查基础配置测试。 */
class MomGatewayApplicationTest {

    @Test
    void gatewayStartsWithDiscoveryRouteAndEdgeFilters() {
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
            assertNotNull(context.getBean(BearerTokenGatewayWebFilter.class));

            Environment environment = context.getEnvironment();
            assertEquals("127.0.0.1", environment.getProperty("spring.data.redis.host"));
            assertEquals("", environment.getProperty("spring.data.redis.password"));
            assertEquals("false", environment.getProperty("spring.cloud.nacos.discovery.enabled"));
            assertEquals("", environment.getProperty("spring.cloud.nacos.discovery.password"));
            assertEquals("false", environment.getProperty("management.otlp.metrics.export.enabled"));
            assertEquals("false", environment.getProperty("management.tracing.export.otlp.enabled"));

            RouteDefinitionLocator locator = context.getBean(RouteDefinitionLocator.class);
            List<RouteDefinition> routes = locator.getRouteDefinitions()
                    .collectList()
                    .block(Duration.ofSeconds(5));
            assertNotNull(routes);

            RouteDefinition systemRoute = routes.stream()
                    .filter(route -> "system-api".equals(route.getId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(URI.create("lb://mom-system-server"), systemRoute.getUri());
            assertTrue(systemRoute.getPredicates().stream()
                    .anyMatch(predicate -> predicate.getArgs().containsValue("/api/system/**")));

            RouteDefinition integrationRoute = routes.stream()
                    .filter(route -> "integration-service".equals(route.getId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(URI.create("lb://mom-integration-server"), integrationRoute.getUri());
        }
    }
}
