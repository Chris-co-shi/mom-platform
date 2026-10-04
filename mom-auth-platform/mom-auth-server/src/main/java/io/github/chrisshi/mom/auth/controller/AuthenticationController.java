package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.AuthenticationApplication;
import io.github.chrisshi.mom.auth.application.UserApplication;
import io.github.chrisshi.mom.auth.application.model.UserView;
import io.github.chrisshi.mom.auth.controller.request.UpdateOwnProfileRequest;
import io.github.chrisshi.mom.auth.controller.request.ChangeOwnPasswordRequest;
import io.github.chrisshi.mom.auth.controller.request.LoginRequest;
import io.github.chrisshi.mom.auth.controller.response.CurrentSessionResponse;
import io.github.chrisshi.mom.auth.controller.response.LoginResponse;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mini Auth 登录、登出及当前账户的 HTTP 协议边界。
 *
 * <p>Controller 只负责请求校验、协议对象转换和统一 {@link Result} 包装；用户名密码认证、
 * Token 签发与注销由 {@link AuthenticationApplication} 编排。Logout 使用 Spring Security 已建立的
 * {@link BearerTokenAuthentication}，禁止在这里手工解析 Authorization Header。资料读写由 UserApplication
 * 负责；Controller 无共享可变状态，不开启事务，不在依赖失败时伪造会话。</p>
 */
@RestController
public class AuthenticationController {

    private final AuthenticationApplication authenticationApplication;
    private final UserApplication userApplication;

    public AuthenticationController(AuthenticationApplication authenticationApplication, UserApplication userApplication) {
        this.authenticationApplication = authenticationApplication;
        this.userApplication = userApplication;
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
     * 查询实时用户资料与当前已认证 Token 快照。
     *
     * <p>以可信主体 ID 查询资料；权限和有效期仍来自 Token。GET 幂等无写副作用；
     * 用户已删除返回 404，数据库故障直接失败。停用不撤销既有 Token，保持 V1 行为。</p>
     *
     * @param authentication Spring Security 已验证的 Bearer Token Authentication
     * @return 当前 Token 的用户标识、稳定排序权限快照和过期时间
     */
    @GetMapping("/me")
    public Result<CurrentSessionResponse> me(BearerTokenAuthentication authentication) {
        return Result.success(currentSession(userApplication.get(authentication.getName()), authentication));
    }

    /**
     * 修改本人显示名，无需管理员权限，身份不从请求体取得。
     * @param authentication 已验证身份
     * @param request 显示名与版本；重复旧版本拒绝
     * @return 最新资料和原有 Token 快照；校验、缺失与冲突交由统一异常边界转换
     */
    @PutMapping("/me/profile")
    public Result<CurrentSessionResponse> updateProfile(BearerTokenAuthentication authentication,
                                                       @Valid @RequestBody UpdateOwnProfileRequest request) {
        return Result.success(currentSession(userApplication.updateOwnProfile(
            authentication.getName(), request.displayName(), request.version()), authentication));
    }

    /**
     * 确认原密码后修改本人密码，不改变 Token 生命周期。
     * @param authentication 已验证身份
     * @param request 原密码、新密码与版本；不接收目标用户 ID
     * @return 最新资料及原有快照；原密码错误为 400，版本冲突为 409
     */
    @PutMapping("/me/password")
    public Result<CurrentSessionResponse> changePassword(BearerTokenAuthentication authentication,
                                                        @Valid @RequestBody ChangeOwnPasswordRequest request) {
        return Result.success(currentSession(userApplication.changeOwnPassword(
            authentication.getName(), request.currentPassword(), request.newPassword(), request.version()), authentication));
    }

    /** 组合实时资料与已验证快照，不重新查询权限、不回显凭据、不延长 Token。 */
    private CurrentSessionResponse currentSession(UserView user, BearerTokenAuthentication authentication) {
        return new CurrentSessionResponse(
            new CurrentSessionResponse.UserProfile(user.id(), user.username(), user.displayName(), user.version()),
            new CurrentSessionResponse.Authorization(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList()),
            new CurrentSessionResponse.Session(authentication.getToken().getExpiresAt())
        );
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
