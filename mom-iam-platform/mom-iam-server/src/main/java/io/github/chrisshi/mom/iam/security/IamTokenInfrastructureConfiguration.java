package io.github.chrisshi.mom.iam.security;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * IAM JWK、JWT Encoder/Decoder、统一 Claims 与 Access Token 签发基础设施装配。
 *
 * <p>配置继续复用唯一 JWKSource 和 Spring Authorization Server Token Generator，不创建第二套密钥。
 * 标准授权码流程仍在当前 HTTP 请求上交付首次 Refresh；第一方 JSON 通过协议无关签发器使用同一
 * Claims Assembler。client_credentials 只保留 SAS 标准服务主体和 Scope，不尝试加载用户身份或创建 Session。
 * 配置或签名失败时启动或请求失败关闭。</p>
 */
@Configuration(proxyBeanMethods = false)
class IamTokenInfrastructureConfiguration {

    @Bean
    AuthorizationServerSettings iamAuthorizationServerSettings(
            IamAuthorizationProperties properties,
            Environment environment) {
        properties.validate();
        IamRsaKeyMaterial.requireProductionIssuer(properties.getIssuer(), environment);
        return AuthorizationServerSettings.builder()
                .issuer(properties.getIssuer().toString())
                .build();
    }

    @Bean
    JWKSource<SecurityContext> iamJwkSource(
            IamAuthorizationProperties properties,
            Environment environment) {
        return IamRsaKeyMaterial.load(properties, environment);
    }

    @Bean
    JwtEncoder iamJwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder iamJwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    IamJwtClaimsAssembler iamJwtClaimsAssembler() {
        return new IamJwtClaimsAssembler();
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> iamJwtCustomizer(
            IamAuthorizationContextLoader contexts,
            IamSessionTokenService sessions,
            IamJwtClaimsAssembler claimsAssembler) {
        return context -> {
            if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(
                    context.getAuthorizationGrantType())) {
                return;
            }
            Authentication principal = context.getPrincipal();
            if (principal == null || principal.getName() == null) {
                return;
            }
            IamAuthorizationContext authorization = contexts.loadByUsername(principal.getName());
            String clientId = context.getRegisteredClient().getClientId();
            claimsAssembler.applyIdentity(context.getClaims(), authorization, clientId);
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                String sessionId = null;
                if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(
                        context.getAuthorizationGrantType())) {
                    HttpServletRequest request = currentRequest();
                    IamSessionTokenService.InitialIssue initial = sessions.issueInitial(
                            principal.getName(),
                            clientId,
                            request.getRemoteAddr(),
                            request.getHeader("User-Agent"),
                            request.getHeader("X-Device-Name"));
                    authorization = initial.authorization();
                    sessionId = initial.sessionId();
                    request.setAttribute(
                            IamSessionTokenService.REQUEST_REFRESH_TOKEN_ATTRIBUTE,
                            initial.refreshToken());
                    request.setAttribute(
                            IamSessionTokenService.REQUEST_SESSION_ID_ATTRIBUTE,
                            initial.sessionId());
                }
                claimsAssembler.applyAccessAuthorization(
                        context.getClaims(), authorization, sessionId);
            }
            if ("id_token".equals(context.getTokenType().getValue())) {
                claimsAssembler.applyOidcIdentity(context.getClaims(), authorization);
            }
        };
    }

    @Bean
    OAuth2TokenGenerator<?> iamTokenGenerator(
            JwtEncoder encoder,
            OAuth2TokenCustomizer<JwtEncodingContext> customizer) {
        JwtGenerator jwtGenerator = new JwtGenerator(encoder);
        jwtGenerator.setJwtCustomizer(customizer);
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator,
                new OAuth2AccessTokenGenerator());
    }

    @Bean({"iamAccessTokenIssuer", "iamSessionJwtIssuer"})
    IamAccessTokenIssuer iamAccessTokenIssuer(
            JwtEncoder encoder,
            AuthorizationServerSettings settings,
            IamAuthorizationProperties properties,
            IamJwtClaimsAssembler claimsAssembler) {
        return new IamAccessTokenIssuer(encoder, settings, properties, claimsAssembler);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new IllegalStateException("Token 签发缺少当前 HTTP 请求");
    }
}
