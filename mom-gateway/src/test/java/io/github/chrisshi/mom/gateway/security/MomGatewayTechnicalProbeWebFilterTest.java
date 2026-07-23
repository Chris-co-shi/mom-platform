package io.github.chrisshi.mom.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gateway 技术探针默认隐藏和 Smoke 显式开启的回归测试。 */
class MomGatewayTechnicalProbeWebFilterTest {

    @Test
    void disabledProbeShouldReturnNotFoundWithoutCallingDownstream() {
        MomGatewayTechnicalProbeWebFilter filter = new MomGatewayTechnicalProbeWebFilter(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/integration/mdm-probe").build());
        AtomicBoolean called = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            called.set(true);
            return Mono.empty();
        }).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertFalse(called.get());
    }

    @Test
    void enabledProbeAndOrdinaryBusinessPathShouldContinue() {
        assertContinues(new MomGatewayTechnicalProbeWebFilter(true), "/api/integration/mdm-probe");
        assertContinues(new MomGatewayTechnicalProbeWebFilter(false), "/api/integration/deliveries");
    }

    private static void assertContinues(MomGatewayTechnicalProbeWebFilter filter, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        AtomicBoolean called = new AtomicBoolean();
        filter.filter(exchange, ignored -> {
            called.set(true);
            return Mono.empty();
        }).block();
        assertTrue(called.get());
    }
}

