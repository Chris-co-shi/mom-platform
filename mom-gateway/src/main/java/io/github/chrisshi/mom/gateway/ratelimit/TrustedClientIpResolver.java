package io.github.chrisshi.mom.gateway.ratelimit;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于显式可信代理边界解析 Gateway 真实客户端 IP。
 *
 * <p>该组件属于 Gateway 限流入口边界，不依赖 Servlet、认证上下文或外部基础设施。
 * TCP 对端不可信时始终忽略 {@code X-Forwarded-For}；只有对端命中显式 IPv4 CIDR，
 * 且 Header 恠好包含一个合法 IPv4 字面量时才采用该值。空值、非法值和多层链均回退到 TCP 对端。</p>
 *
 * <p>可信 CIDR 在构造时预解析成不可变列表，后续请求只做纯内存匹配，可并发安全复用。
 * 本版本不推导 CDN/WAF/多层代理链，避免在边界信息不足时猜测客户端身份。</p>
 */
public final class TrustedClientIpResolver {

    static final String UNKNOWN_CLIENT_IP = "unknown";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final List<Ipv4Subnet> trustedProxies;

    /**
     * 创建解析器并校验全部可信代理 CIDR。
     *
     * @param properties 可信代理配置
     * @throws IllegalArgumentException 配置包含非法或非 IPv4 CIDR 时抛出
     */
    public TrustedClientIpResolver(TrustedClientIpProperties properties) {
        this.trustedProxies = parseTrustedProxies(properties.trustedProxies());
    }

    /**
     * 按单层可信代理规则解析客户端 IP。
     *
     * @param exchange 当前响应式请求上下文
     * @return 标准化 IP 字面量；缺失可用 TCP 对端时返回 {@code unknown}
     */
    public String resolve(ServerWebExchange exchange) {
        InetAddress remoteAddress = resolveRemoteAddress(exchange);
        if (remoteAddress == null) {
            return UNKNOWN_CLIENT_IP;
        }
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress.getHostAddress();
        }

        String forwardedClient = resolveSingleForwardedIpv4(exchange.getRequest().getHeaders());
        return forwardedClient == null ? remoteAddress.getHostAddress() : forwardedClient;
    }

    private boolean isTrustedProxy(InetAddress remoteAddress) {
        if (!(remoteAddress instanceof Inet4Address)) {
            return false;
        }
        int candidate = ipv4ToInt(remoteAddress.getAddress());
        return trustedProxies.stream().anyMatch(subnet -> subnet.contains(candidate));
    }

    private static InetAddress resolveRemoteAddress(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress == null ? null : remoteAddress.getAddress();
    }

    private static String resolveSingleForwardedIpv4(HttpHeaders headers) {
        List<String> values = headers.get(X_FORWARDED_FOR);
        if (values == null || values.size() != 1) {
            return null;
        }
        String candidate = values.getFirst();
        if (candidate == null || candidate.contains(",")) {
            return null;
        }
        byte[] address = parseIpv4Literal(candidate.trim());
        return address == null ? null : formatIpv4(address);
    }

    private static List<Ipv4Subnet> parseTrustedProxies(List<String> configuredCidrs) {
        List<Ipv4Subnet> result = new ArrayList<>();
        for (String configuredCidr : configuredCidrs) {
            if (configuredCidr == null || configuredCidr.isBlank()) {
                continue;
            }
            result.add(Ipv4Subnet.parse(configuredCidr.trim()));
        }
        return List.copyOf(result);
    }

    private static byte[] parseIpv4Literal(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return null;
        }
        byte[] address = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(Character::isDigit)) {
                return null;
            }
            int number;
            try {
                number = Integer.parseInt(octet);
            }
            catch (NumberFormatException exception) {
                return null;
            }
            if (number > 255) {
                return null;
            }
            address[index] = (byte) number;
        }
        return address;
    }

    private static String formatIpv4(byte[] address) {
        return Byte.toUnsignedInt(address[0]) + "."
                + Byte.toUnsignedInt(address[1]) + "."
                + Byte.toUnsignedInt(address[2]) + "."
                + Byte.toUnsignedInt(address[3]);
    }

    private static int ipv4ToInt(byte[] address) {
        return Byte.toUnsignedInt(address[0]) << 24
                | Byte.toUnsignedInt(address[1]) << 16
                | Byte.toUnsignedInt(address[2]) << 8
                | Byte.toUnsignedInt(address[3]);
    }

    private record Ipv4Subnet(int network, int mask) {

        private static Ipv4Subnet parse(String cidr) {
            String[] parts = cidr.split("/", -1);
            byte[] address = parts.length == 2 ? parseIpv4Literal(parts[0]) : null;
            int prefixLength;
            try {
                prefixLength = parts.length == 2 ? Integer.parseInt(parts[1]) : -1;
            }
            catch (NumberFormatException exception) {
                throw invalidCidr(cidr);
            }
            if (address == null || prefixLength < 0 || prefixLength > 32) {
                throw invalidCidr(cidr);
            }
            int mask = prefixLength == 0 ? 0 : -1 << (32 - prefixLength);
            return new Ipv4Subnet(ipv4ToInt(address) & mask, mask);
        }

        private boolean contains(int address) {
            return (address & mask) == network;
        }

        private static IllegalArgumentException invalidCidr(String cidr) {
            return new IllegalArgumentException(
                    "mom.gateway.client-ip.trusted-proxies 仅接受合法 IPv4 CIDR: " + cidr);
        }
    }
}
