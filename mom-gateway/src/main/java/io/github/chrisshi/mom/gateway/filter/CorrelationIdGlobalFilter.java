package io.github.chrisshi.mom.gateway.filter;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.core.context.CorrelationHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 全局关联标识 Filter。
 *
 * <p>Gateway 是 WebFlux 应用，不能复用 Servlet ThreadLocal Filter。该实现直接修改不可变的
 * {@link ServerHttpRequest}，把关联标识写入下游请求和客户端响应。缺失 Header 时生成 UUID，已有值则
 * 原样保留，从而让外部请求、Gateway 日志和后续服务共享同一标识。</p>
 *
 * <p>这里只调用 {@link CorrelationContext#resolveOrGenerate(String)} 的纯值处理能力，不把关联标识写入
 * ThreadLocal，避免在 Reactor 线程切换和事件循环复用时产生上下文污染。</p>
 */
@Component
public final class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 为请求解析或生成关联标识，并传播到下游与响应 Header。
     *
     * @param exchange 当前响应式请求上下文
     * @param chain Gateway 后续过滤器链
     * @return 请求处理完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = CorrelationContext.resolveOrGenerate(
                exchange.getRequest().getHeaders().getFirst(CorrelationHeaders.CORRELATION_ID));

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(CorrelationHeaders.CORRELATION_ID, correlationId))
                .build();

        exchange.getResponse().getHeaders().set(CorrelationHeaders.CORRELATION_ID, correlationId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    /**
     * 在路由、鉴权和限流前建立关联标识，便于后续组件记录统一日志。
     *
     * @return GlobalFilter 排序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
