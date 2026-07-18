package io.github.chrisshi.mom.ratelimit;

import io.github.chrisshi.mom.core.context.CorrelationHeaders;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Gateway Redis 限流故障处理器。
 *
 * <p>当前阶段采用 fail-closed：当 MOM 包装器识别到官方 RedisRateLimiter 的异常放行结果，或者
 * 其他自定义限流实现直接抛出 Redis 异常时，不允许请求继续访问下游，而是返回 HTTP 503。这样可以
 * 避免 Redis 故障期间所有 Gateway 实例同时失去流量保护，冲击 IAM、Integration 或后续 MES/WMS
 * 核心服务。</p>
 *
 * <p>该处理器只接管限流基础设施异常，其他异常继续交给 Gateway 默认错误处理链。</p>
 */
public final class RedisRateLimitFailureWebExceptionHandler implements WebExceptionHandler, Ordered {

    private static final byte[] RESPONSE_BODY = ("{\"code\":\"REDIS_RATE_LIMIT_UNAVAILABLE\","
            + "\"message\":\"网关限流基础设施暂时不可用\"}")
            .getBytes(StandardCharsets.UTF_8);

    /**
     * 处理 Gateway 过滤链抛出的异常。
     *
     * @param exchange 当前请求与响应上下文
     * @param exception 过滤链异常
     * @return 限流基础设施故障时写入 503 响应；其他异常继续向后传播
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (!isRateLimitInfrastructureFailure(exception) || exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(CorrelationHeaders.CORRELATION_ID);
        if (correlationId != null && !correlationId.isBlank()) {
            exchange.getResponse().getHeaders().set(
                    CorrelationHeaders.CORRELATION_ID,
                    correlationId.trim());
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(RESPONSE_BODY);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 递归检查异常因果链，兼容 MOM fail-closed 包装器以及 Reactor 对原始 Redis 异常的包装。
     */
    private static boolean isRateLimitInfrastructureFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RedisRateLimitUnavailableException
                    || current instanceof RedisConnectionFailureException
                    || current instanceof RedisSystemException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 必须早于 Gateway 默认错误处理器执行，才能把限流基础设施故障稳定映射为 503。
     *
     * @return WebExceptionHandler 顺序
     */
    @Override
    public int getOrder() {
        return -2;
    }
}
