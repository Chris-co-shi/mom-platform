package io.github.chrisshi.mom.gateway.filter;

import io.github.chrisshi.mom.gateway.error.GatewayErrorCode;
import io.github.chrisshi.mom.gateway.error.GatewayException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Gateway 的 Bearer Header 浅层格式检查器。
 *
 * <p>该过滤器不维护业务公开路径清单，也不要求每个请求都必须携带凭证。当
 * {@code Authorization} 存在时，它仅拒绝重复 Header 或明显不合法的 Bearer 格式；Token 真伪、
 * 有效期与授权由下游 Resource Server 判定。OPTIONS 请求不做 Bearer 边缘检查，以保证
 * CORS 预检在没有凭证时仍可正常处理。</p>
 *
 * <p>合法的 {@code Authorization} Header 保持原值继续向下游转发；错误统一抛出 GatewayException，
 * 由 GatewayExceptionHandler 生成稳定 HTTP 响应。</p>
 */
@Component
public final class BearerTokenGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 对已提供的 Authorization 执行浅层格式检查，不产生认证状态且不访问外部基础设施。
     *
     * @param exchange 当前响应式请求上下文
     * @param chain Gateway 后续过滤器链
     * @return 请求处理完成信号；格式非法时以 {@link GatewayException} 失败
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        List<String> authorizationHeaders = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            return chain.filter(exchange);
        }
        if (authorizationHeaders.size() != 1 || !isValidBearerHeader(authorizationHeaders.getFirst())) {
            return Mono.error(new GatewayException(GatewayErrorCode.INVALID_BEARER_TOKEN));
        }

        return chain.filter(exchange);
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

    /**
     * 在路由前拒绝明显的凭证格式污染，但不介入业务认证决策。
     *
     * @return GlobalFilter 排序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
