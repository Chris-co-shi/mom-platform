package io.github.chrisshi.mom.bootstrap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.chrisshi.mom.core.context.CorrelationHeaders;
import io.github.chrisshi.mom.integration.MomIntegrationApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationMdmServiceCallTest {

    private HttpServer mdmStub;

    @AfterEach
    void stopMdmStub() {
        if (mdmStub != null) {
            mdmStub.stop(0);
        }
    }

    @Test
    void integrationCallsMdmAndPropagatesCorrelationId() throws Exception {
        AtomicReference<String> downstreamCorrelationId = new AtomicReference<>();
        mdmStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mdmStub.createContext("/internal/mdm/probe",
                exchange -> handleMdmProbe(exchange, downstreamCorrelationId));
        mdmStub.start();

        String mdmUrl = "http://127.0.0.1:" + mdmStub.getAddress().getPort();
        String correlationId = "p01-s02-correlation-001";

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MomIntegrationApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "spring.main.banner-mode=off",
                        "spring.cloud.nacos.discovery.enabled=false",
                        "spring.cloud.nacos.config.enabled=false",
                        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                        "spring.cloud.openfeign.client.config.mom-mdm-server.url=" + mdmUrl)
                .run()) {
            Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
            assertNotNull(port);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/integration/mdm-probe"))
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
            assertEquals(correlationId, downstreamCorrelationId.get());
            assertTrue(response.body().contains("\"service\":\"mom-integration-server\""));
            assertTrue(response.body().contains("\"mdmService\":\"mom-mdm-server\""));
            assertTrue(response.body().contains("\"mdmCorrelationId\":\"" + correlationId + "\""));
        }
    }

    private static void handleMdmProbe(
            HttpExchange exchange,
            AtomicReference<String> downstreamCorrelationId) throws IOException {
        String correlationId = exchange.getRequestHeaders()
                .getFirst(CorrelationHeaders.CORRELATION_ID);
        downstreamCorrelationId.set(correlationId);

        byte[] body = ("{\"service\":\"mom-mdm-server\","
                + "\"status\":\"UP\","
                + "\"correlationId\":\"" + correlationId + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
