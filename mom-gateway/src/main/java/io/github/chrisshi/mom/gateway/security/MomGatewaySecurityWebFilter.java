package io.github.chrisshi.mom.gateway.security;

import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import io.github.chrisshi.mom.security.token.MomSecurityClaims;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Gateway 受保护请求后置安全过滤器。
 *
 * <p>过滤器在 JWT Authentication 之后、Authorization 之前运行：删除客户端伪造的 {@code X-MOM-*}
 * Header，执行 Client 路由隔离，并查询 IAM 写入的 revoked sid。Redis 超时或故障一律返回 503，绝不把
 * 不可用解释为“未撤销”。原始 {@code Authorization: Bearer} 保留并转发给业务 Resource Server。</p>
 */
public final class MomGatewaySecurityWebFilter implements WebFilter, Ordered {
    private final ReactiveStringRedisTemplate redis;
    private final MomGatewaySecurityProperties properties;
    private final MomGatewayClientRoutePolicy routePolicy;

    public MomGatewaySecurityWebFilter(
            ReactiveStringRedisTemplate redis,
            MomGatewaySecurityProperties properties,
            MomGatewayClientRoutePolicy routePolicy) {
        this.redis = redis;
        this.properties = properties;
        this.routePolicy = routePolicy;
    }

    @Override
    public int getOrder() {
        return SecurityWebFiltersOrder.AUTHORIZATION.getOrder() - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerWebExchange sanitized = sanitizeIdentityHeaders(exchange);
        String path = sanitized.getRequest().getURI().getPath();
        if (!path.startsWith("/api/")) {
            return chain.filter(sanitized);
        }

        return sanitized.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .flatMap(authentication -> authorize(sanitized, chain, authentication))
                .switchIfEmpty(chain.filter(sanitized));
    }

    private Mono<Void> authorize(
            ServerWebExchange exchange,
            WebFilterChain chain,
            JwtAuthenticationToken authentication) {
        MomJwtAuthorization authorization;
        try {
            authorization = MomJwtAuthorization.from(authentication.getToken());
        }
        catch (IllegalArgumentException exception) {
            return write(exchange, HttpStatus.UNAUTHORIZED, "invalid_token");
        }
        if (!routePolicy.isAllowed(exchange.getRequest().getURI().getPath(), authorization)) {
            return write(exchange, HttpStatus.FORBIDDEN, "client_route_forbidden");
        }

        String sid = MomSecurityClaims.stringClaim(
                authentication.getToken(), MomSecurityClaims.SESSION_ID);
        if (sid == null) {
            return write(exchange, HttpStatus.UNAUTHORIZED, "invalid_token");
        }
        return redis.hasKey(properties.getRevokedSidKeyPrefix() + sid)
                .timeout(properties.getRedisTimeout())
                .flatMap(revoked -> Boolean.TRUE.equals(revoked)
                        ? write(exchange, HttpStatus.UNAUTHORIZED, "session_revoked")
                        : chain.filter(exchange))
                .onErrorResume(exception -> write(
                        exchange, HttpStatus.SERVICE_UNAVAILABLE, "revocation_store_unavailable"));
    }

    private static ServerWebExchange sanitizeIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            for (String name : new ArrayList<>(headers.keySet())) {
                if (name.toUpperCase(Locale.ROOT).startsWith("X-MOM-")) {
                    headers.remove(name);
                }
            }
        }).build();
        return exchange.mutate().request(request).build();
    }

    private static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String error) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        byte[] body = ("{\"error\":\"" + error + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
