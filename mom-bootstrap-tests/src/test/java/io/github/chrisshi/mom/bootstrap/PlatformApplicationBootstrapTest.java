package io.github.chrisshi.mom.bootstrap;

import io.github.chrisshi.mom.iam.MomIamApplication;
import io.github.chrisshi.mom.integration.MomIntegrationApplication;
import io.github.chrisshi.mom.mdm.MomMdmApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformApplicationBootstrapTest {

    @Test
    void iamStartsWithoutExternalInfrastructure() {
        assertApplicationStarts(MomIamApplication.class);
    }

    @Test
    void mdmStartsWithoutExternalInfrastructure() {
        assertApplicationStarts(MomMdmApplication.class);
    }

    @Test
    void integrationStartsWithoutExternalInfrastructure() {
        assertApplicationStarts(MomIntegrationApplication.class);
    }

    private void assertApplicationStarts(Class<?> applicationClass) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(applicationClass)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "spring.main.banner-mode=off",
                        "spring.cloud.nacos.discovery.enabled=false",
                        "spring.cloud.nacos.config.enabled=false",
                        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                        "management.endpoints.enabled-by-default=true")
                .run()) {
            assertTrue(context.isActive());
            assertNotNull(context.getBean(HealthEndpoint.class));
        }
    }
}
