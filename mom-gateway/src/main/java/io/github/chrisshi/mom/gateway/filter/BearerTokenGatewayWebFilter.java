package io.github.chrisshi.mom.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Gateway 的 Bearer Token 边缘检查与内部 Header 清洗。
 *
 * <p>该过滤器只负责在流量入口拒绝明显缺失或格式非法的 Bearer Credential，并删除客户端伪造的
 * {@code X-MOM-*} Header。它不解析、不验证 Token，不访问 Redis，也不构造 Authentication；
 * Token 的真实性、有效期与授权信息由下游 Resource Server 通过 mom-security 完成验证。</p>
 *
 * <p>合法的 {@code Authorization} Header 保持原值继续向下游转发。</p>
 */
@Component
public final class BearerTokenGatewayWebFilter implements WebFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Set<String> PUBLIC_API_PATHS = Set.of(
        "/api/auth/login",
        "/api/auth/test"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerWebExchange sanitized = sanitizeInternalHeaders(exchange);
        ServerHttpRequest request = sanitized.getRequest();
        String path = request.getPath().value();

        if (HttpMethod.OPTIONS.equals(request.getMethod())
            || PUBLIC_API_PATHS.contains(path)) {
            return chain.filter(sanitized);
        }

        List<String> authorizationHeaders = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            return writeUnauthorized(sanitized, "missing_bearer_token");
        }
        if (authorizationHeaders.size() != 1 || !isValidBearerHeader(authorizationHeaders.getFirst())) {
            return writeUnauthorized(sanitized, "invalid_bearer_token");
        }

        return chain.filter(sanitized);
    }

    private static boolean isValidBearerHeader(String value) {
        if (value == null || value.length() <= BEARER_PREFIX.length()) {
            return false;
        }
        if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return false;
        }

        String token = value.substring(BEARER_PREFIX.length());
        if (token.isBlank()) {
            return false;
        }
        return token.chars().noneMatch(Character::isWhitespace);
    }

    private static ServerWebExchange sanitizeInternalHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            for (String name : new ArrayList<>(headers.headerNames())) {
                if (name.toUpperCase(Locale.ROOT).startsWith("X-MOM-")) {
                    headers.remove(name);
                }
            }
        }).build();
        return exchange.mutate().request(request).build();
    }

    private static Mono<Void> writeUnauthorized(ServerWebExchange exchange, String error) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        byte[] body = ("{\"error\":\"" + error + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
