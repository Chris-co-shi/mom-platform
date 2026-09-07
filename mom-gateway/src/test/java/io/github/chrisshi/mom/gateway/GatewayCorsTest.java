package io.github.chrisshi.mom.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Gateway CORS 真实 WebFlux 请求行为测试。 */
class GatewayCorsTest {

    @Test
    void preflightWithoutBearerMustAllowConfiguredOrigin() {
        try (ConfigurableApplicationContext context = startGateway()) {
            webTestClient(context)
                    .options()
                    .uri("/auth/users")
                    .header(HttpHeaders.ORIGIN, "https://console.example.com")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://console.example.com")
                    .expectHeader().valueEquals(
                            HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Correlation-Id");
        }
    }

    @Test
    void preflightMustNotGrantUnconfiguredOrigin() {
        try (ConfigurableApplicationContext context = startGateway()) {
            webTestClient(context)
                    .options()
                    .uri("/auth/users")
                    .header(HttpHeaders.ORIGIN, "https://evil.example")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        }
    }

    private static ConfigurableApplicationContext startGateway() {
        return new SpringApplicationBuilder(MomGatewayApplication.class)
                .web(WebApplicationType.REACTIVE)
                .run(
                        "--server.port=0",
                        "--spring.main.banner-mode=off",
                        "--spring.cloud.nacos.discovery.enabled=false",
                        "--spring.cloud.nacos.config.enabled=false",
                        "--MOM_GATEWAY_CORS_ALLOWED_ORIGINS=https://console.example.com,https://ops.example.com");
    }

    private static WebTestClient webTestClient(ConfigurableApplicationContext context) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
    }
}
