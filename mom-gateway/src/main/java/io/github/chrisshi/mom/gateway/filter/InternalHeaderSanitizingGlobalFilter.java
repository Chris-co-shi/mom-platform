package io.github.chrisshi.mom.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/**
 * Gateway 外部入口的内部身份 Header 清理器。
 *
 * <p>该类属于 WebFlux 边缘适配层，只删除公网客户端不得伪造的用户、角色、权限、
 * Factory 和 Party 身份 Header。它不解析 Token、不建立 Authentication，也不依赖下游服务。
 * 过滤器每次请求只创建新的不可变请求视图，没有共享可变状态，适用于 Reactor 并发模型。</p>
 *
 * <p>{@code Authorization}、{@code X-Correlation-Id} 和普通业务 Header 不在清理范围内。
 * 过滤器本身不访问外部基础设施，因此不存在降级或远程失败分支。</p>
 */
@Component
public final class InternalHeaderSanitizingGlobalFilter implements GlobalFilter, Ordered {

    private static final String MOM_HEADER_PREFIX = "X-MOM-";
    private static final Set<String> INTERNAL_IDENTITY_HEADERS = Set.of(
            "X-USER-ID",
            "X-USER-NAME",
            "X-ROLE",
            "X-ROLES",
            "X-PERMISSION",
            "X-PERMISSIONS",
            "X-FACTORY-ID",
            "X-PARTY-ID");

    /**
     * 删除外部请求中的内部身份 Header，其他 Header 保持原样传递。
     *
     * @param exchange 当前响应式请求上下文
     * @param chain Gateway 后续过滤器链
     * @return 请求处理完成信号；无外部资源副作用
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            for (String name : new ArrayList<>(headers.headerNames())) {
                if (isInternalIdentityHeader(name)) {
                    headers.remove(name);
                }
            }
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static boolean isInternalIdentityHeader(String name) {
        String normalized = name.toUpperCase(Locale.ROOT);
        return normalized.startsWith(MOM_HEADER_PREFIX) || INTERNAL_IDENTITY_HEADERS.contains(normalized);
    }

    /**
     * 在 Bearer 格式检查和路由之前清理不可信身份输入。
     *
     * @return GlobalFilter 排序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
