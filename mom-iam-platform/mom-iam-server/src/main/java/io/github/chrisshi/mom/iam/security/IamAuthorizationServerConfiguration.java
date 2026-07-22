package io.github.chrisshi.mom.iam.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamAuthorizationCatalogRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamIdentityBindingRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamUserAccessRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamUserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2TokenType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.web.OAuth2AuthorizationEndpointFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/** P1.5 S03 Authorization Server、账号认证、四 Client 与 JWK/JWT 基础配置。 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(IamAuthorizationProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(
        prefix = "mom.iam.authorization",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IamAuthorizationServerConfiguration {

    @Bean
    PasswordEncoder iamPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    IamAccountAuthenticationService iamAccountAuthenticationService(
            IamUserRepository users,
            PasswordEncoder passwordEncoder,
            IamAuthorizationProperties properties,
            Clock clock) {
        properties.validate();
        return new IamAccountAuthenticationService(users, passwordEncoder, properties, clock);
    }

    @Bean
    AuthenticationProvider iamAuthenticationProvider(
            IamAccountAuthenticationService accounts,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    IamClientAccessPolicyService iamClientAccessPolicyService(
            IamAccountAuthenticationService accounts,
            IamAuthorizationCatalogRepository catalog,
            IamIdentityBindingRepository bindings,
            IamUserAccessRepository accessRepository,
            Clock clock) {
        return new IamClientAccessPolicyService(accounts, catalog, bindings, accessRepository, clock);
    }

    @Bean
    RequestCache iamRequestCache() {
        return new HttpSessionRequestCache();
    }

    @Bean
    SavedRequestAwareAuthenticationSuccessHandler iamSavedRequestSuccessHandler() {
        SavedRequestAwareAuthenticationSuccessHandler handler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/");
        return handler;
    }

    @Bean
    AuthenticationSuccessHandler iamLoginSuccessHandler(
            IamAccountAuthenticationService accounts,
            SavedRequestAwareAuthenticationSuccessHandler continuation) {
        return (request, response, authentication) -> {
            IamUserEntity user = accounts.recordSuccessfulLogin(authentication.getName());
            if (Boolean.TRUE.equals(user.getPasswordChangeRequired())) {
                response.sendRedirect(request.getContextPath() + "/password/change");
                return;
            }
            continuation.onAuthenticationSuccess(request, response, authentication);
        };
    }

    @Bean
    AuthenticationFailureHandler iamLoginFailureHandler(IamAccountAuthenticationService accounts) {
        SimpleUrlAuthenticationFailureHandler delegate =
                new SimpleUrlAuthenticationFailureHandler("/login?error");
        return (request, response, exception) -> {
            if (exception instanceof BadCredentialsException) {
                accounts.recordBadCredentials(request.getParameter("username"));
            }
            delegate.onAuthenticationFailure(request, response, exception);
        };
    }

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
        return arguments -> {
            properties.validate();
            for (IamAuthorizationProperties.ClientRegistration registration : properties.registrations()) {
                RegisteredClient existing = repository.findByClientId(registration.clientId());
                String id = existing == null ? registration.clientId() : existing.getId();
                repository.save(publicClient(id, registration));
            }
        };
    }

    private static RegisteredClient publicClient(
            String id, IamAuthorizationProperties.ClientRegistration registration) {
        return RegisteredClient.withId(id)
                .clientId(registration.clientId())
                .clientName(registration.clientName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(registration.client().getRedirectUri().toString())
                .postLogoutRedirectUri(registration.client().getPostLogoutRedirectUri().toString())
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(Duration.ofMinutes(5))
                        .accessTokenTimeToLive(Duration.ofMinutes(10))
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                        .build())
                .build();
    }

    @Bean
    AuthorizationServerSettings iamAuthorizationServerSettings(
            IamAuthorizationProperties properties,
            Environment environment) {
        properties.validate();
        requireProductionIssuer(properties.getIssuer(), environment);
        return AuthorizationServerSettings.builder()
                .issuer(properties.getIssuer().toString())
                .build();
    }

    @Bean
    JWKSource<SecurityContext> iamJwkSource(
            IamAuthorizationProperties properties,
            Environment environment) {
        properties.validate();
        IamAuthorizationProperties.SigningKey key = properties.getKey();
        rejectTestKeyInProduction(key, environment);
        RSAPrivateKey privateKey = readPrivateKey(key.getPrivateKeyLocation());
        RSAPublicKey publicKey = readPublicKey(key.getPublicKeyLocation());
        if (!privateKey.getModulus().equals(publicKey.getModulus())
                || publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalStateException("IAM RSA 公私钥不匹配或长度小于 2048 位");
        }
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(key.getKeyId())
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    JwtDecoder iamJwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> iamJwtCustomizer(
            IamAccountAuthenticationService accounts) {
        return context -> {
            Authentication principal = context.getPrincipal();
            if (principal == null || principal.getName() == null) {
                return;
            }
            IamUserEntity user = accounts.requireUser(principal.getName());
            context.getClaims()
                    .subject(user.getId())
                    .claim("client_id", context.getRegisteredClient().getClientId())
                    .claim("user_type", user.getUserType().name());
            if ("id_token".equals(context.getTokenType().getValue())) {
                context.getClaims()
                        .claim("preferred_username", user.getUsername())
                        .claim("name", user.getDisplayName());
            }
        };
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain iamAuthorizationServerSecurityFilterChain(
            HttpSecurity http,
            IamAccountAuthenticationService accounts,
            IamClientAccessPolicyService accessPolicy,
            RequestCache requestCache) throws Exception {
        PkceS256AuthorizationRequestFilter pkceFilter = new PkceS256AuthorizationRequestFilter();
        IamClientAuthorizationRequestFilter accessFilter = new IamClientAuthorizationRequestFilter(
                accounts, accessPolicy, requestCache);
        http.oauth2AuthorizationServer(authorizationServer -> {
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    authorizationServer.oidc(Customizer.withDefaults());
                })
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .addFilterBefore(pkceFilter, OAuth2AuthorizationEndpointFilter.class)
                .addFilterBefore(accessFilter, OAuth2AuthorizationEndpointFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain iamLoginSecurityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider,
            AuthenticationSuccessHandler successHandler,
            AuthenticationFailureHandler failureHandler,
            RequestCache requestCache) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/error", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/password/change").authenticated()
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider)
                .requestCache(cache -> cache.requestCache(requestCache))
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .deleteCookies("MOM_IAM_SESSION"))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.migrateSession()));
        return http.build();
    }

    private static RSAPrivateKey readPrivateKey(Resource resource) {
        try {
            String pem = resource.getContentAsString(StandardCharsets.US_ASCII);
            byte[] encoded = decodePem(pem, "PRIVATE KEY");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded));
        }
        catch (Exception exception) {
            throw new IllegalStateException("IAM RSA 私钥无法读取", exception);
        }
    }

    private static RSAPublicKey readPublicKey(Resource resource) {
        try {
            String pem = resource.getContentAsString(StandardCharsets.US_ASCII);
            byte[] encoded = decodePem(pem, "PUBLIC KEY");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encoded));
        }
        catch (Exception exception) {
            throw new IllegalStateException("IAM RSA 公钥无法读取", exception);
        }
    }

    private static byte[] decodePem(String pem, String type) {
        String normalized = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static void rejectTestKeyInProduction(
            IamAuthorizationProperties.SigningKey key, Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        String privateDescription = key.getPrivateKeyLocation().getDescription();
        String publicDescription = key.getPublicKeyLocation().getDescription();
        if (key.isAllowTestKey()
                || privateDescription.contains("/test/")
                || publicDescription.contains("/test/")) {
            throw new IllegalStateException("生产环境禁止使用 IAM 本地测试签名密钥");
        }
    }

    private static void requireProductionIssuer(URI issuer, Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && !"https".equalsIgnoreCase(issuer.getScheme())) {
            throw new IllegalStateException("生产环境 IAM issuer 必须使用 HTTPS");
        }
    }
}
