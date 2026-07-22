package io.github.chrisshi.mom.gateway.security;

import io.github.chrisshi.mom.security.token.MomSecurityClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** S06 revoked sid、Redis Fail Closed、Client 路由和伪造 Header 清理测试。 */
class MomGatewaySecurityWebFilterTest {
    private ReactiveStringRedisTemplate redis;
    private MomGatewaySecurityProperties properties;
    private MomGatewaySecurityWebFilter filter;

    @BeforeEach
    void setUp() {
        redis = mock(ReactiveStringRedisTemplate.class);
        properties = new MomGatewaySecurityProperties();
        properties.setRedisTimeout(Duration.ofSeconds(1));
        filter = new MomGatewaySecurityWebFilter(
                redis, properties, new MomGatewayClientRoutePolicy());
    }

    @Test
    void activeSessionMustRemoveForgedHeadersAndForwardBearerOnce() {
        when(redis.hasKey("mom:iam:revoked:sid:session-1")).thenReturn(Mono.just(false));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/integration/probe")
                .header(HttpHeaders.AUTHORIZATION, "Bearer signed-jwt")
                .header("X-MOM-User-Id", "attacker")
                .header("x-mom-permissions", "iam:user:write")
                .header("X-Factory-Id", "factory-1")
                .build();
        ServerWebExchange exchange = withPrincipal(
                MockServerWebExchange.from(request), internalAuthentication("mom-admin-web"));
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        WebFilterChain chain = current -> {
            calls.incrementAndGet();
            forwarded.set(current);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertEquals(1, calls.get());
        assertNotNull(forwarded.get());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-MOM-User-Id"));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("x-mom-permissions"));
        assertEquals("Bearer signed-jwt",
                forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("factory-1",
                forwarded.get().getRequest().getHeaders().getFirst("X-Factory-Id"));
    }

    @Test
    void revokedSessionMustReturnUnauthorizedWithoutForwarding() {
        when(redis.hasKey("mom:iam:revoked:sid:session-1")).thenReturn(Mono.just(true));
        ServerWebExchange exchange = withPrincipal(
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/mdm/materials").build()),
                internalAuthentication("mom-admin-web"));
        AtomicInteger calls = new AtomicInteger();

        filter.filter(exchange, current -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertEquals(0, calls.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void redisFailureMustFailClosedWithServiceUnavailable() {
        when(redis.hasKey("mom:iam:revoked:sid:session-1"))
                .thenReturn(Mono.error(new IllegalStateException("redis unavailable")));
        ServerWebExchange exchange = withPrincipal(
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/wms/orders").build()),
                internalAuthentication("mom-mobile-pda"));
        AtomicInteger calls = new AtomicInteger();

        filter.filter(exchange, current -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertEquals(0, calls.get());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    void wrongClientRouteMustBeForbiddenBeforeRedisLookup() {
        ServerWebExchange exchange = withPrincipal(
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/customer/orders").build()),
                internalAuthentication("mom-admin-web"));
        AtomicInteger calls = new AtomicInteger();

        filter.filter(exchange, current -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertEquals(0, calls.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verifyNoInteractions(redis);
    }

    @Test
    void anonymousRequestMustContinueOnceForSecurityChainToReturn401() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/integration/probe").build());
        AtomicInteger calls = new AtomicInteger();

        filter.filter(exchange, current -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertEquals(1, calls.get());
        verifyNoInteractions(redis);
    }

    private static ServerWebExchange withPrincipal(
            ServerWebExchange exchange,
            JwtAuthenticationToken authentication) {
        return exchange.mutate().principal(Mono.just(authentication)).build();
    }

    private static JwtAuthenticationToken internalAuthentication(String clientId) {
        Instant now = Instant.parse("2026-07-22T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("https://iam.mom.example")
                .subject("user-1")
                .audience(List.of(clientId))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plusSeconds(600))
                .claim("jti", "jti-1")
                .claim(MomSecurityClaims.SESSION_ID, "session-1")
                .claim(MomSecurityClaims.CLIENT_ID, clientId)
                .claim(MomSecurityClaims.USER_TYPE, MomSecurityClaims.USER_TYPE_INTERNAL)
                .claim(MomSecurityClaims.ROLES, List.of("PLATFORM_ADMIN"))
                .claim(MomSecurityClaims.PERMISSIONS, List.of("mdm:material:read"))
                .claim(MomSecurityClaims.FACTORY_IDS, List.of("factory-1"))
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
