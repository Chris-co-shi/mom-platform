package io.github.chrisshi.mom.integration.interfaces.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** 本机文档匿名可读；其余本地探针仍要求认证，且服务只绑定 loopback。 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class IntegrationLocalSecurityConfiguration {
    /** 仅放开 OpenAPI 静态文档路径，其他端点保留本机 HTTP Basic 认证。 */
    @Bean
    public SecurityFilterChain integrationLocalSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
