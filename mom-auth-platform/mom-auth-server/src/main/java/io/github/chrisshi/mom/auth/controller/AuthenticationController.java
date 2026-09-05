package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.AuthenticationApplication;
import io.github.chrisshi.mom.auth.controller.request.LoginRequest;
import io.github.chrisshi.mom.auth.controller.response.LoginResponse;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mini Auth 登录/登出的 HTTP 边界。
 *
 * <p>Controller 只负责协议适配与统一 {@link Result}，认证和 Token 生命周期由 Application 编排。</p>
 */
@RestController
public class AuthenticationController {

    private final AuthenticationApplication authenticationApplication;

    public AuthenticationController(AuthenticationApplication authenticationApplication) {
        this.authenticationApplication = authenticationApplication;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(LoginResponse.from(authenticationApplication.login(request.username(), request.password())));
    }

    @PostMapping("/logout")
    public Result<Void> logout(BearerTokenAuthentication authentication) {
        authenticationApplication.logout(authentication.getToken().getTokenValue());
        return Result.success();
    }
}
