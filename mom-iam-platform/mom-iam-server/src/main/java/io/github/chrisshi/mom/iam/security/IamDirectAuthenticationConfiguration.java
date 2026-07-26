package io.github.chrisshi.mom.iam.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * MOM 第一方 JSON 认证端点的独立安全链。
 *
 * <p>链路只匹配 {@code /api/iam/auth/**}，优先级位于 Authorization Server 标准端点之后、IAM
 * 表单兼容链之前。登录、首次改密与 Refresh 不依赖 HttpSession；Logout 必须携带当前 Bearer JWT，
 * 并由控制器按 sid 撤销权威 Session。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean({IamAccountAuthenticationService.class, JwtDecoder.class})
public class IamDirectAuthenticationConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain iamDirectAuthenticationSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/iam/auth/**")
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/iam/auth/login",
                                "/api/iam/auth/password/change-required",
                                "/api/iam/auth/refresh")
                        .permitAll()
                        .requestMatchers("/api/iam/auth/logout")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .build();
    }
}
