package io.github.chrisshi.mom.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Locale;

/**
 * Gateway 请求限流身份解析器。
 *
 * <p>当请求已经完成认证时，优先使用 Principal 名称形成用户级限流 Key；在 IAM 尚未接入或匿名访问时，
 * 使用当前 TCP 连接的远端 IP。当前实现故意不读取 {@code X-Forwarded-For}，因为在未建立可信代理链规则前，
 * 该 Header 可以被客户端伪造，直接用作限流依据会导致绕过或恶意封禁其他用户。</p>
 *
 * <p>Spring Cloud Gateway 会把解析结果与路由标识共同用于 Redis 令牌桶，因此不同路由之间不会共享同一桶。</p>
 */
public final class RequestIdentityKeyResolver implements KeyResolver {

    /**
     * 解析当前请求的限流身份。
     *
     * @param exchange 当前响应式请求上下文
     * @return 非空限流身份；格式为 {@code principal:xxx} 或 {@code ip:xxx}
     */
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .map(Principal::getName)
                .filter(name -> !name.isBlank())
                .map(name -> "principal:" + normalize(name))
                .switchIfEmpty(Mono.fromSupplier(() -> resolveRemoteAddress(exchange)));
    }

    /**
     * 从真实 TCP 连接中提取远端 IP；测试或特殊传输层无法提供地址时使用稳定兜底值。
     */
    private static String resolveRemoteAddress(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "ip:unknown";
        }
        return "ip:" + remoteAddress.getAddress().getHostAddress();
    }

    /**
     * 将 Principal 名称限制在 Redis Key 安全字符集合中，避免空格、控制字符或分隔符污染 Key。
     */
    private static String normalize(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._@-]", "-")
                .replaceAll("-+", "-");
    }
}
