package io.github.chrisshi.mom.gateway.security;

import io.github.chrisshi.mom.security.token.MomJwtGrantedAuthoritiesConverter;
import io.github.chrisshi.mom.security.token.MomJwtValidators;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/** S06 Gateway Reactive Resource Server、Client 路由与 revoked sid 配置。 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(MomGatewaySecurityProperties.class)
@ConditionalOnProperty(
        prefix = "mom.gateway.security",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MomGatewaySecurityConfiguration {

    @Bean
    ReactiveJwtDecoder momGatewayJwtDecoder(MomGatewaySecurityProperties properties) {
        properties.validate();
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(properties.getJwkSetUri())
                .build();
        decoder.setJwtValidator(MomJwtValidators.create(
                properties.getIssuerUri(), properties.getAcceptedAudiences()));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain momGatewaySecurityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder) {
        JwtAuthenticationConverter servletConverter = new JwtAuthenticationConverter();
        servletConverter.setJwtGrantedAuthoritiesConverter(new MomJwtGrantedAuthoritiesConverter());
        ReactiveJwtAuthenticationConverterAdapter reactiveConverter =
                new ReactiveJwtAuthenticationConverterAdapter(servletConverter);

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(authorize -> authorize
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt
                        .jwtDecoder(jwtDecoder)
                        .jwtAuthenticationConverter(reactiveConverter)))
                .build();
    }

    @Bean
    MomGatewayClientRoutePolicy momGatewayClientRoutePolicy() {
        return new MomGatewayClientRoutePolicy();
    }

    @Bean
    MomGatewaySecurityWebFilter momGatewaySecurityWebFilter(
            ReactiveStringRedisTemplate redis,
            MomGatewaySecurityProperties properties,
            MomGatewayClientRoutePolicy routePolicy) {
        properties.validate();
        return new MomGatewaySecurityWebFilter(redis, properties, routePolicy);
    }
}
