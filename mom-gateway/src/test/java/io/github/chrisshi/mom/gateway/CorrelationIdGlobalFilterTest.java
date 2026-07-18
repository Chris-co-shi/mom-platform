package io.github.chrisshi.mom.gateway;

import io.github.chrisshi.mom.core.context.CorrelationHeaders;
import io.github.chrisshi.mom.gateway.filter.CorrelationIdGlobalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CorrelationIdGlobalFilterTest {

    private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

    @Test
    void generatesAndForwardsCorrelationIdWhenMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/integration/mdm-probe").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        String requestCorrelationId = forwarded.get().getRequest().getHeaders()
                .getFirst(CorrelationHeaders.CORRELATION_ID);
        String responseCorrelationId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationHeaders.CORRELATION_ID);

        assertFalse(requestCorrelationId == null || requestCorrelationId.isBlank());
        assertEquals(requestCorrelationId, responseCorrelationId);
    }

    @Test
    void preservesExistingCorrelationId() {
        String correlationId = "existing-correlation-id";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/integration/mdm-probe")
                        .header(CorrelationHeaders.CORRELATION_ID, correlationId)
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        assertEquals(correlationId, forwarded.get().getRequest().getHeaders()
                .getFirst(CorrelationHeaders.CORRELATION_ID));
        assertEquals(correlationId, exchange.getResponse().getHeaders()
                .getFirst(CorrelationHeaders.CORRELATION_ID));
    }
}
