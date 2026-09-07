package io.github.chrisshi.mom.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InternalHeaderSanitizingGlobalFilterTest {

    private final InternalHeaderSanitizingGlobalFilter filter = new InternalHeaderSanitizingGlobalFilter();

    @Test
    void mustRemoveIdentityHeadersCaseInsensitivelyAndPreserveNonIdentityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/qms/results")
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token-value")
                .header("X-MOM-User-Id", "attacker")
                .header("x-mom-permissions", "qms:result:approve")
                .header("X-MOM-Role", "ADMIN")
                .header("X-Factory-Id", "factory-1")
                .header("X-Party-Id", "party-1")
                .header("X-Correlation-Id", "correlation-1")
                .header("X-Business-Header", "business-value")
                .build();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(MockServerWebExchange.from(request), current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertNull(headers.getFirst("X-MOM-User-Id"));
        assertNull(headers.getFirst("x-mom-permissions"));
        assertNull(headers.getFirst("X-MOM-Role"));
        assertNull(headers.getFirst("X-Factory-Id"));
        assertNull(headers.getFirst("X-Party-Id"));
        assertEquals("Bearer opaque-token-value", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("correlation-1", headers.getFirst("X-Correlation-Id"));
        assertEquals("business-value", headers.getFirst("X-Business-Header"));
    }
}
