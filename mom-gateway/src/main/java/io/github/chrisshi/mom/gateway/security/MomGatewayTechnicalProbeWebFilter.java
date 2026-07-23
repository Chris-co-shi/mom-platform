package io.github.chrisshi.mom.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Gateway 的 Phase 01 技术探针入口门禁。
 *
 * <p>通用 Integration 路由仍服务后续正式业务 API，但两个历史技术探针只有在受控 Smoke 显式设置
 * {@code mom.gateway.technical-probe.enabled=true} 时才允许转发。默认返回 404，避免生产环境通过路由结构
 * 推断或访问技术接口；该过滤器不承担业务 JWT、Permission 或 Scope 最终授权。</p>
 */
@Component
public final class MomGatewayTechnicalProbeWebFilter implements WebFilter, Ordered {

    private static final Set<String> TECHNICAL_PROBE_PATHS = Set.of(
            "/api/integration/mdm-probe",
            "/api/integration/idempotency-probe");

    private final boolean enabled;

    /**
     * 创建技术探针入口门禁。
     *
     * @param enabled 是否允许受控 Smoke 访问历史技术探针；默认必须为 false
     */
    public MomGatewayTechnicalProbeWebFilter(
            @Value("${mom.gateway.technical-probe.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 在路由与限流之前隐藏默认关闭的技术探针。
     *
     * @param exchange 当前请求交换对象
     * @param chain 后续 Gateway 过滤链
     * @return 完成信号；拒绝时直接以 404 结束且不调用下游
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enabled && TECHNICAL_PROBE_PATHS.contains(exchange.getRequest().getPath().value())) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    /**
     * 早于安全、限流和路由执行，确保关闭状态不会产生下游访问或 Redis 依赖。
     *
     * @return 最高优先级之后的稳定顺序
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}

