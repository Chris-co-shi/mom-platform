package io.github.chrisshi.mom.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 请求限流身份解析器。
 *
 * <p>该解析器始终通过 {@link TrustedClientIpResolver} 生成 {@code ip:<client-ip>} Key。
 * Mini Auth V1 的 Gateway 不建立 Authentication，因此不读取 Principal、不 introspect Opaque Token、
 * 不执行 Redis Token Store 查询。IP Key 只用于粗粒度边缘保护，不表示用户身份或精准配额。</p>
 */
public final class RequestIdentityKeyResolver implements KeyResolver {

    private final TrustedClientIpResolver trustedClientIpResolver;

    /**
     * 创建基于可信客户端 IP 的限流 Key 解析器。
     *
     * @param trustedClientIpResolver 可信客户端 IP 解析器
     */
    public RequestIdentityKeyResolver(TrustedClientIpResolver trustedClientIpResolver) {
        this.trustedClientIpResolver = trustedClientIpResolver;
    }

    /**
     * 生成当前请求的 IP 限流 Key。
     *
     * @param exchange 当前响应式请求上下文
     * @return 始终包含一个 {@code ip:} Key 的 Mono；无远端地址时使用 {@code ip:unknown}
     */
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return Mono.just("ip:" + trustedClientIpResolver.resolve(exchange));
    }
}
