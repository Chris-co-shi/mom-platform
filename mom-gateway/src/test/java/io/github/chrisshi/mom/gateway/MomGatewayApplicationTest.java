package io.github.chrisshi.mom.gateway;

import io.github.chrisshi.mom.gateway.filter.BearerTokenGlobalFilter;
import io.github.chrisshi.mom.gateway.filter.CorrelationIdGlobalFilter;
import io.github.chrisshi.mom.gateway.filter.InternalHeaderSanitizingGlobalFilter;
import io.github.chrisshi.mom.gateway.ratelimit.TrustedClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;

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
                .run(
                        "--server.port=0",
                        "--spring.main.banner-mode=off",
                        "--spring.cloud.nacos.discovery.enabled=false",
                        "--spring.cloud.nacos.config.enabled=false",
                        "--management.endpoints.enabled-by-default=true")) {
            assertTrue(context.isActive());
            assertNotNull(context.getBean(HealthEndpoint.class));
            assertNotNull(context.getBean(CorrelationIdGlobalFilter.class));
            assertNotNull(context.getBean(BearerTokenGlobalFilter.class));
            assertNotNull(context.getBean(InternalHeaderSanitizingGlobalFilter.class));
            assertNotNull(context.getBean(TrustedClientIpResolver.class));

            Environment environment = context.getEnvironment();
            assertEquals("127.0.0.1", environment.getProperty("spring.data.redis.host"));
            assertEquals("", environment.getProperty("spring.data.redis.password"));
            assertEquals("false", environment.getProperty("spring.cloud.nacos.discovery.enabled"));
            assertEquals("", environment.getProperty("spring.cloud.nacos.discovery.password"));
            assertEquals("false", environment.getProperty("management.otlp.metrics.export.enabled"));
            assertEquals("false", environment.getProperty("management.tracing.export.otlp.enabled"));
            assertEquals("true", environment.getProperty(
                    "spring.cloud.gateway.server.webflux.globalcors.add-to-simple-url-handler-mapping"));

            CorsConfiguration cors = context.getBean(GlobalCorsProperties.class)
                    .getCorsConfigurations()
                    .get("/**");
            assertNotNull(cors);
            assertEquals(List.of("http://localhost:5173"), cors.getAllowedOrigins());
            assertTrue(cors.getAllowedMethods().contains(HttpMethod.GET.name()));
            assertEquals(List.of("Authorization", "Content-Type", "X-Correlation-Id"),
                    cors.getAllowedHeaders());
            assertEquals(List.of("X-Correlation-Id"), cors.getExposedHeaders());
            assertEquals(false, cors.getAllowCredentials());

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
            assertTrue(systemRoute.getFilters().stream()
                    .noneMatch(filter -> "StripPrefix".equals(filter.getName())));

            RouteDefinition integrationRoute = routes.stream()
                    .filter(route -> "integration-service".equals(route.getId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(URI.create("lb://mom-integration-server"), integrationRoute.getUri());
        }
    }
}
