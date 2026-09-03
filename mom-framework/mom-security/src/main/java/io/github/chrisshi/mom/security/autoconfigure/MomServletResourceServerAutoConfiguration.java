package io.github.chrisshi.mom.security.autoconfigure;

import io.github.chrisshi.mom.security.token.MomOpaqueTokenIntrospector;
import io.github.chrisshi.mom.security.token.MomTokenStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;

/**
 * Servlet 业务服务的默认 Resource Server 安全配置。
 *
 * <p>第一版使用 Redis-backed Opaque Token。Framework 负责提供大多数业务服务一致的安全基线：
 * 无状态请求、公开路径、Bearer Token 认证与方法级授权。Token 的实际查询和认证信息构造由
 * {@link OpaqueTokenIntrospector} 完成。</p>
 *
 * <p>业务服务如有特殊 HTTP 安全策略，可以自行声明 {@link SecurityFilterChain}，此默认配置会自动退让。</p>
 */
@AutoConfiguration(
    after = MomSecurityTokenAutoConfiguration.class
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, OpaqueTokenIntrospector.class})
@ConditionalOnProperty(
    prefix = "mom.security.resource-server",
    name = "enabled",
    havingValue = "true"
)
@EnableConfigurationProperties(MomResourceServerProperties.class)
@EnableMethodSecurity
public class MomServletResourceServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OpaqueTokenIntrospector.class)
    OpaqueTokenIntrospector momOpaqueTokenIntrospector(
        MomTokenStore tokenStore,
        ObjectProvider<Clock> clockProvider
    ) {
        Clock clock = clockProvider.getIfAvailable(Clock::systemUTC);

        return new MomOpaqueTokenIntrospector(
            tokenStore,
            clock
        );
    }

    @Bean("momResourceServerSecurityFilterChain")
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain momResourceServerSecurityFilterChain(
        HttpSecurity http,
        OpaqueTokenIntrospector introspector,
        MomResourceServerProperties properties
    ) {

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authorize -> {
                if (!properties.getPublicPaths().isEmpty()) {
                    authorize
                        .requestMatchers(properties.getPublicPaths().toArray(String[]::new))
                        .permitAll();
                }
                authorize.anyRequest().authenticated();
            })
            .oauth2ResourceServer(resourceServer ->
                resourceServer.opaqueToken(opaque ->
                    opaque.introspector(introspector)
                )
            );

        return http.build();
    }
}
