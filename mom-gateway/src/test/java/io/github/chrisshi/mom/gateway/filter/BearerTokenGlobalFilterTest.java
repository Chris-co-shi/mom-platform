package io.github.chrisshi.mom.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BearerTokenGlobalFilterTest {

    private final BearerTokenGlobalFilter filter = new BearerTokenGlobalFilter();

    @Test
    void protectedApiWithoutBearerMustReturnUnauthorized() {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/mes/work-orders").build());
        AtomicInteger calls = new AtomicInteger();

        filter.filter(exchange, current -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertEquals(0, calls.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void malformedBearerMustReturnUnauthorized() {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/wms/inventory")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token with spaces")
                .build());
        AtomicInteger calls = new AtomicInteger();

        filter.filter(exchange, current -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertEquals(0, calls.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void validBearerMustBeForwardedUnchangedAndMomHeadersRemoved() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/qms/results")
            .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token-value")
            .header("X-MOM-User-Id", "attacker")
            .header("x-mom-permissions", "qms:result:approve")
            .header("X-Factory-Id", "factory-1")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        assertNotNull(forwarded.get());
        assertEquals("Bearer opaque-token-value",
            forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-MOM-User-Id"));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("x-mom-permissions"));
        assertEquals("factory-1",
            forwarded.get().getRequest().getHeaders().getFirst("X-Factory-Id"));
    }

//    @Test
//    void loginAndOptionsMustNotRequireBearer() {
//        AtomicInteger calls = new AtomicInteger();
//        WebFilterChain chain = current -> {
//            calls.incrementAndGet();
//            return Mono.empty();
//        };
//
//        filter.filter(MockServerWebExchange.from(
//            MockServerHttpRequest.post("/api/auth/login").build()), chain).block();
//        filter.filter(MockServerWebExchange.from(
//            MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/mes/work-orders").build()), chain).block();
//
//        assertEquals(2, calls.get());
//    }

    @Test
    void duplicateAuthorizationHeadersMustBeRejected() {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/mes/work-orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer first", "Bearer second")
                .build());
        AtomicInteger calls = new AtomicInteger();

        filter.filter(exchange, current -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertEquals(0, calls.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
}
