package io.github.chrisshi.mom.gateway.filter;

import io.github.chrisshi.mom.gateway.error.GatewayErrorCode;
import io.github.chrisshi.mom.gateway.error.GatewayException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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
 * <p>合法的 {@code Authorization} Header 保持原值继续向下游转发；错误统一抛出 GatewayException，
 * 由 GatewayExceptionHandler 生成稳定 HTTP 响应。</p>
 */
@Component
public final class BearerTokenGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Set<String> PUBLIC_API_PATHS = Set.of(
            "/auth/login"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitized = sanitizeInternalHeaders(exchange);
        ServerHttpRequest request = sanitized.getRequest();
        String path = request.getPath().value();

        if (PUBLIC_API_PATHS.contains(path)) {
            return chain.filter(sanitized);
        }

        List<String> authorizationHeaders = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            return Mono.error(new GatewayException(GatewayErrorCode.MISSING_BEARER_TOKEN));
        }
        if (authorizationHeaders.size() != 1 || !isValidBearerHeader(authorizationHeaders.getFirst())) {
            return Mono.error(new GatewayException(GatewayErrorCode.INVALID_BEARER_TOKEN));
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
        return !token.isBlank() && token.chars().noneMatch(Character::isWhitespace);
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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
