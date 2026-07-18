package io.github.chrisshi.mom.bootstrap;

import io.github.chrisshi.mom.core.context.CorrelationHeaders;
import io.github.chrisshi.mom.integration.MomIntegrationApplication;
import io.github.chrisshi.mom.mdm.MomMdmApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationMdmServiceCallTest {

    private static final String BOOTSTRAP_EXCLUSIONS = String.join(",",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration");

    @Test
    void integrationCallsRealMdmApplicationAndPropagatesCorrelationId() throws Exception {
        try (ConfigurableApplicationContext mdmContext = startApplication(MomMdmApplication.class)) {
            Integer mdmPort = localPort(mdmContext);
            String mdmUrl = "http://127.0.0.1:" + mdmPort;

            try (ConfigurableApplicationContext integrationContext = startApplication(
                    MomIntegrationApplication.class,
                    "spring.cloud.openfeign.client.config.mom-mdm-server.url=" + mdmUrl)) {
                Integer integrationPort = localPort(integrationContext);
                String correlationId = "p01-s02-correlation-001";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + integrationPort
                                + "/integration/mdm-probe"))
                        .header(CorrelationHeaders.CORRELATION_ID, correlationId)
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.ofString());

                assertEquals(200, response.statusCode());
                assertEquals(correlationId, response.headers()
                        .firstValue(CorrelationHeaders.CORRELATION_ID)
                        .orElseThrow());
                assertTrue(response.body().contains("\"service\":\"mom-integration-server\""));
                assertTrue(response.body().contains("\"mdmService\":\"mom-mdm-server\""));
                assertTrue(response.body().contains("\"correlationId\":\"" + correlationId + "\""));
                assertTrue(response.body().contains("\"mdmCorrelationId\":\"" + correlationId + "\""));
            }
        }
    }

    private static ConfigurableApplicationContext startApplication(
            Class<?> applicationClass,
            String... additionalProperties) {
        List<String> properties = new ArrayList<>(List.of(
                "server.port=0",
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.autoconfigure.exclude=" + BOOTSTRAP_EXCLUSIONS));
        properties.addAll(Arrays.asList(additionalProperties));

        return new SpringApplicationBuilder(applicationClass)
                .web(WebApplicationType.SERVLET)
                .properties(properties.toArray(String[]::new))
                .run();
    }

    private static Integer localPort(ConfigurableApplicationContext context) {
        Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        assertNotNull(port);
        return port;
    }
}
