package io.github.chrisshi.mom.gateway.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * 显式关闭 Gateway Auth 时的无认证链。
 *
 * <p>该配置只在 {@code mom.gateway.security.enabled=false} 时生效，用于旧基础设施、限流与可观测性
 * Smoke 独立验证。若不注册该链，Spring Boot 会因 Security Starter 自动创建 Basic Login，导致这些专项
 * Smoke 返回 401。生产默认仍由 {@link MomGatewaySecurityConfiguration} 的 JWT、Audience 与 revoked sid
 * Fail Closed 链接管。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "mom.gateway.security",
        name = "enabled",
        havingValue = "false")
public class MomGatewaySecurityDisabledConfiguration {

    @Bean
    SecurityWebFilterChain momGatewaySecurityDisabledWebFilterChain(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(authorize -> authorize.anyExchange().permitAll())
                .build();
    }
}
