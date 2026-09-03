package io.github.chrisshi.mom.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Locale;

/**
 * Gateway 请求限流身份解析器。
 *
 * <p>若请求上下文已有 Principal，优先使用 Principal 名称；当前 Mini Auth V1 的 Gateway 不负责认证，
 * 因此正常外部请求通常回退到真实 TCP 远端 IP。未建立可信代理链前不读取 X-Forwarded-For。</p>
 */
public final class RequestIdentityKeyResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .map(Principal::getName)
                .filter(name -> !name.isBlank())
                .map(name -> "principal:" + normalize(name))
                .switchIfEmpty(Mono.fromSupplier(() -> resolveRemoteAddress(exchange)));
    }

    private static String resolveRemoteAddress(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "ip:unknown";
        }
        return "ip:" + remoteAddress.getAddress().getHostAddress();
    }

    private static String normalize(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._@-]", "-")
                .replaceAll("-+", "-");
    }
}
