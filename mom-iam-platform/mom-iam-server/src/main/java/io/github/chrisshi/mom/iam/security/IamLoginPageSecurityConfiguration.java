package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import io.github.chrisshi.mom.iam.web.IamAuthenticationPageController;
import io.github.chrisshi.mom.iam.web.IamMeController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.security.Principal;

import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RouterFunctions.route;

/**
 * IAM HTML 登录页、Cookie Session 与普通 Bearer API 的第二优先级安全装配。
 *
 * <p>本配置保持页面 Form Login、CSRF 默认、Session Fixation、Logout Cookie、公开健康端点和
 * {@code /api/iam/me} 鉴权不变；它不承载第一方 JSON 登录业务或 SAS 标准协议实现。</p>
 */
@Configuration(proxyBeanMethods = false)
class IamLoginPageSecurityConfiguration {

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
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession()));
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
            IamAuthorizationContextLoader contexts,
            IamClientAccessPolicyService clientAccess,
            IamScopeGuard scopeGuard) {
        return new IamMeController(contexts, clientAccess, scopeGuard);
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
