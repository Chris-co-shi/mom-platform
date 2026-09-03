package io.github.chrisshi.mom.gateway.error;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayExceptionHandlerTest {

    @Test
    void shouldSerializeStableErrorResponseWithoutHandwrittenJson() {
        GatewayExceptionHandler handler = new GatewayExceptionHandler(
                JsonMapper.builder().build(),
                new StaticMessageSource());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/mes/work-orders").build());

        handler.handle(exchange, new GatewayException(GatewayErrorCode.MISSING_BEARER_TOKEN)).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals("no-store", exchange.getResponse().getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body.contains("\"code\":\"missing_bearer_token\""));
        assertTrue(body.contains("\"message\":\"缺少 Bearer Token\""));
    }
}
