package io.github.chrisshi.mom.iam.security;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Spring Authorization Server 标准协议、官方 JDBC Store 与最高优先级 FilterChain 装配。
 *
 * <p>本配置保持 Authorization/OIDC/Token/JWK/Discovery 端点、PKCE S256、Public Client、标准错误
 * 与官方 JDBC Store 不变，同时注册可选的 System client_credentials 服务身份。协议异常继续由 SAS 处理，
 * 不进入 MOM 第一方 ControllerAdvice。</p>
 */
@Configuration(proxyBeanMethods = false)
class IamAuthorizationServerProtocolConfiguration {

    @Bean
    RegisteredClientRepository iamRegisteredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    OAuth2AuthorizationService iamOAuth2AuthorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClients) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClients);
    }

    @Bean
    OAuth2AuthorizationConsentService iamOAuth2AuthorizationConsentService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClients);
    }

    @Bean
    ApplicationRunner iamRegisteredClientInitializer(
            RegisteredClientRepository repository,
            IamAuthorizationProperties properties) {
        return new IamPublicClientRegistrar(repository, properties);
    }

    /** 注册启用后的 mom-system-server client_credentials 服务身份。 */
    @Bean
    ApplicationRunner iamSystemServiceClientInitializer(
            RegisteredClientRepository repository,
            IamAuthorizationProperties properties,
            PasswordEncoder passwordEncoder) {
        return new IamSystemServiceClientRegistrar(repository, properties, passwordEncoder);
    }

    @Bean
    IamRefreshGrantAuthenticationProvider iamRefreshGrantAuthenticationProvider(
            IamSessionTokenService sessions,
            IamAccessTokenIssuer tokenIssuer) {
        return new IamRefreshGrantAuthenticationProvider(sessions, tokenIssuer);
    }

    @Bean
    IamTokenResponseHandler iamTokenResponseHandler() {
        return new IamTokenResponseHandler();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain iamAuthorizationServerSecurityFilterChain(
            HttpSecurity http,
            IamAccountAuthenticationService accounts,
            IamClientAccessPolicyService accessPolicy,
            RequestCache requestCache,
            RegisteredClientRepository registeredClients,
            OAuth2TokenGenerator<?> tokenGenerator,
            IamRefreshGrantAuthenticationProvider refreshProvider,
            IamTokenResponseHandler tokenResponseHandler) throws Exception {
        PkceS256AuthorizationRequestFilter pkceFilter = new PkceS256AuthorizationRequestFilter();
        IamClientAuthorizationRequestFilter accessFilter = new IamClientAuthorizationRequestFilter(
                accounts, accessPolicy, requestCache);
        http.oauth2AuthorizationServer(authorizationServer -> {
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    authorizationServer
                            .clientAuthentication(clientAuthentication -> clientAuthentication
                                    .authenticationConverter(
                                            new IamPublicRefreshClientAuthenticationConverter())
                                    .authenticationProvider(
                                            new IamPublicRefreshClientAuthenticationProvider(
                                                    registeredClients)))
                            .tokenGenerator(tokenGenerator)
                            .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                                    .accessTokenRequestConverter(
                                            new IamRefreshGrantAuthenticationConverter())
                                    .authenticationProvider(refreshProvider)
                                    .accessTokenResponseHandler(tokenResponseHandler))
                            .oidc(Customizer.withDefaults());
                })
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .addFilterAfter(pkceFilter, SecurityContextHolderFilter.class)
                .addFilterAfter(accessFilter, SecurityContextHolderFilter.class);
        return http.build();
    }
}
