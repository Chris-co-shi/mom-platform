package io.github.chrisshi.mom.security.autoconfigure;

import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import io.github.chrisshi.mom.security.revocation.MomRevokedSessionChecker;
import io.github.chrisshi.mom.security.revocation.MomRevokedSessionFilter;
import io.github.chrisshi.mom.security.revocation.infrastructure.RedisMomRevokedSessionChecker;
import io.github.chrisshi.mom.security.token.MomJwtValidators;
import io.github.chrisshi.mom.security.token.MomSecurityClaims;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 可选的 Servlet 业务 Resource Server 自动配置。
 *
 * <p>默认关闭，业务服务通过 {@code mom.security.resource-server.enabled=true} 显式启用。启用后每个服务
 * 独立验证 Bearer JWT，不信任 Gateway 注入的身份 Header；最终 Permission、Factory/Party 与对象归属仍由
 * 业务代码调用 {@link MomAuthorizationService} 判定。</p>
 */
@AutoConfiguration(after = MomSecurityActorAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, JwtAuthenticationProvider.class})
@ConditionalOnProperty(
    prefix = "mom.security.resource-server",
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties(MomResourceServerProperties.class)
@EnableMethodSecurity
public class MomServletResourceServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MomAuthorizationService momAuthorizationService() {
        return new MomAuthorizationService();
    }

    @Bean
    @ConditionalOnMissingBean
    JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter() {
        JwtGrantedAuthoritiesConverter converter =
            new JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName(
            MomSecurityClaims.AUTHORITIES
        );
        converter.setAuthorityPrefix("");
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean
    MomRevokedSessionChecker momRevokedSessionChecker(
        StringRedisTemplate redis,
        MomResourceServerProperties properties) {
        properties.validate();
        return new RedisMomRevokedSessionChecker(redis, properties.getRevokedSidKeyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    MomRevokedSessionFilter momRevokedSessionFilter(
        MomRevokedSessionChecker checker,
        MomResourceServerProperties properties) {
        return new MomRevokedSessionFilter(checker, properties.getPublicPaths());
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder momJwtDecoder(MomResourceServerProperties properties) {
        properties.validate();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        decoder.setJwtValidator(MomJwtValidators.create(
            properties.getIssuerUri(), properties.getAcceptedAudiences()));
        return decoder;
    }

    @Bean("momResourceServerSecurityFilterChain")
    @ConditionalOnMissingBean(name = "momResourceServerSecurityFilterChain")
    SecurityFilterChain momResourceServerSecurityFilterChain(
        HttpSecurity http,
        JwtDecoder jwtDecoder,
        JwtGrantedAuthoritiesConverter authoritiesConverter,
        MomRevokedSessionFilter revokedSessionFilter,
        MomResourceServerProperties properties) throws Exception {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> {
                if (!properties.getPublicPaths().isEmpty()) {
                    authorize.requestMatchers(properties.getPublicPaths().toArray(String[]::new)).permitAll();
                }
                authorize.anyRequest().authenticated();
            })
            .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt
                .decoder(jwtDecoder)
                .jwtAuthenticationConverter(authenticationConverter)))
            .addFilterAfter(revokedSessionFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
