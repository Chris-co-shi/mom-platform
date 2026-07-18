package io.github.chrisshi.mom.bootstrap;

import io.github.chrisshi.mom.gateway.MomGatewayApplication;
import io.github.chrisshi.mom.iam.MomIamApplication;
import io.github.chrisshi.mom.integration.MomIntegrationApplication;
import io.github.chrisshi.mom.mdm.MomMdmApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformApplicationBootstrapTest {

    @Test
    void gatewayStartsWithoutExternalInfrastructure() {
        assertApplicationStarts(MomGatewayApplication.class, WebApplicationType.REACTIVE);
    }

    @Test
    void iamStartsWithoutExternalInfrastructure() {
        assertApplicationStarts(MomIamApplication.class, WebApplicationType.SERVLET);
    }

    @Test
    void mdmStartsWithoutExternalInfrastructure() {
        assertApplicationStarts(MomMdmApplication.class, WebApplicationType.SERVLET);
    }

    @Test
    void integrationStartsWithoutExternalInfrastructure() {
        assertApplicationStarts(MomIntegrationApplication.class, WebApplicationType.SERVLET);
    }

    private void assertApplicationStarts(Class<?> applicationClass, WebApplicationType webApplicationType) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(applicationClass)
                .web(webApplicationType)
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
