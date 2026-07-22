package io.github.chrisshi.mom.iam.security;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamAuthorizationCatalogRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamAuthorizationContextRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamIdentityBindingRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamUserAccessRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamUserRepository;
import io.github.chrisshi.mom.iam.web.IamAuthenticationPageController;
import io.github.chrisshi.mom.iam.web.IamMeController;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import javax.sql.DataSource;
import java.security.Principal;
import java.time.Clock;

import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RouterFunctions.route;

/** P1.5 S03 Authorization Server 与 S04 RBAC/Scope/Me 自动配置。 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
        "io.github.chrisshi.mom.iam.autoconfigure.IamPersistenceRepositoryAutoConfiguration"
})
@EnableWebSecurity
@EnableConfigurationProperties(IamAuthorizationProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean({DataSource.class, IamUserRepository.class})
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
    IamAuthorizationContextService iamAuthorizationContextService(
            IamUserRepository users,
            IamAuthorizationContextRepository contexts,
            Clock clock) {
        return new IamAuthorizationContextService(users, contexts, clock);
    }

    @Bean
    IamScopeGuard iamScopeGuard() {
        return new IamScopeGuard();
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
    @Primary
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
        return new IamPublicClientRegistrar(repository, properties);
    }

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
    JwtDecoder iamJwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> iamJwtCustomizer(
            IamAuthorizationContextService contexts) {
        return context -> {
            Authentication principal = context.getPrincipal();
            if (principal == null || principal.getName() == null) {
                return;
            }
            IamAuthorizationContext authorization = contexts.loadByUsername(principal.getName());
            context.getClaims()
                    .subject(authorization.userId())
                    .claim("client_id", context.getRegisteredClient().getClientId())
                    .claim("user_type", authorization.userType().name());
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims()
                        .claim("roles", authorization.roles())
                        .claim("permissions", authorization.permissions())
                        .claim("factory_ids", authorization.factoryIds());
                if (authorization.externalPartyBound()) {
                    context.getClaims()
                            .claim("party_type", authorization.partyType().name())
                            .claim("party_id", authorization.partyId());
                }
            }
            if ("id_token".equals(context.getTokenType().getValue())) {
                context.getClaims()
                        .claim("preferred_username", authorization.username())
                        .claim("name", authorization.displayName());
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
                .addFilterAfter(pkceFilter, SecurityContextHolderFilter.class)
                .addFilterAfter(accessFilter, SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain iamLoginAndApiSecurityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider,
            AuthenticationSuccessHandler successHandler,
            AuthenticationFailureHandler failureHandler,
            RequestCache requestCache) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/error", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/password/change", "/api/iam/me").authenticated()
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider)
                .requestCache(cache -> cache.requestCache(requestCache))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
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

    @Bean
    IamAuthenticationPageController iamAuthenticationPageController(
            IamAccountAuthenticationService accounts,
            SavedRequestAwareAuthenticationSuccessHandler continuation) {
        return new IamAuthenticationPageController(accounts, continuation);
    }

    @Bean
    IamMeController iamMeController(
            IamAuthorizationContextService contexts,
            IamScopeGuard scopeGuard) {
        return new IamMeController(contexts, scopeGuard);
    }

    @Bean
    RouterFunction<ServerResponse> iamMeRoutes(IamMeController controller) {
        return route(GET("/api/iam/me"), request -> {
            Principal principal = request.principal()
                    .orElseThrow(() -> new IllegalStateException("缺少已认证用户"));
            if (!(principal instanceof Authentication authentication)) {
                throw new IllegalStateException("认证主体类型无效");
            }
            IamMeController.IamMeResponse response = controller.me(
                    authentication,
                    request.headers().firstHeader(IamMeController.CURRENT_FACTORY_HEADER));
            return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(response);
        });
    }
}
