package io.github.chrisshi.mom.security.autoconfigure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * MOM 业务服务 Resource Server 的通用配置。
 *
 * <p>第一版采用 Redis-backed Opaque Token。认证数据的读取由
 * {@code OpaqueTokenIntrospector} 负责，因此这里不再承载 JWT Issuer、JWK、Audience
 * 或 Token 撤销存储等实现细节。</p>
 */
@Getter
@ConfigurationProperties("mom.security.resource-server")
public class MomResourceServerProperties {

    @Setter
    private boolean enabled;

    private List<String> publicPaths = List.of(
        "/actuator/health/**",
        "/error"
    );

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
