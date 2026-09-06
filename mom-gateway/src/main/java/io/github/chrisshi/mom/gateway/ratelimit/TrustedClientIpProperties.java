package io.github.chrisshi.mom.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Gateway 真实客户端 IP 解析的可信代理配置。
 *
 * <p>该配置属于 Gateway 边缘基础设施边界，只接受部署方明确给出的 IPv4 CIDR，
 * 不从请求 Header 或服务发现动态扩展信任。对象构造后为不可变快照，可在 Reactor 请求间并发共享；
 * 非法 CIDR 会在 Bean 创建时使启动失败，避免正式环境带着错误的信任边界运行。</p>
 *
 * @param trustedProxies 允许生成单值 {@code X-Forwarded-For} 的代理 IPv4 CIDR 列表
 */
@ConfigurationProperties("mom.gateway.client-ip")
public record TrustedClientIpProperties(List<String> trustedProxies) {

    /**
     * 创建不可变配置快照，空配置表示不信任任何代理。
     *
     * @param trustedProxies 可信代理 IPv4 CIDR 列表
     */
    public TrustedClientIpProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }
}
