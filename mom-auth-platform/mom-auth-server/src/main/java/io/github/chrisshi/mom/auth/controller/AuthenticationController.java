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
 * Mini Auth 登录/登出的 HTTP 协议边界。
 *
 * <p>Controller 只负责请求校验、协议对象转换和统一 {@link Result} 包装；用户名密码认证、
 * Token 签发与注销由 {@link AuthenticationApplication} 编排。Logout 使用 Spring Security 已建立的
 * {@link BearerTokenAuthentication}，禁止在这里手工解析 Authorization Header。</p>
 */
@RestController
public class AuthenticationController {

    private final AuthenticationApplication authenticationApplication;

    public AuthenticationController(AuthenticationApplication authenticationApplication) {
        this.authenticationApplication = authenticationApplication;
    }

    /**
     * 用户名密码登录。
     *
     * @param request 已通过 Bean Validation 的登录请求
     * @return 统一 Result 包装的 Bearer Token 响应
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(LoginResponse.from(authenticationApplication.login(request.username(), request.password())));
    }

    /**
     * 注销当前 Bearer Token。
     *
     * <p>只有已经通过 Resource Server 验证的请求才能进入该方法；首次注销删除 Redis Token，
     * 后续再次携带同一 Token 时应在认证阶段因 Token 不存在而得到 401。</p>
     *
     * @param authentication Spring Security 已验证的 Bearer Token Authentication
     * @return 空数据的统一成功结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(BearerTokenAuthentication authentication) {
        authenticationApplication.logout(authentication.getToken().getTokenValue());
        return Result.success();
    }
}
