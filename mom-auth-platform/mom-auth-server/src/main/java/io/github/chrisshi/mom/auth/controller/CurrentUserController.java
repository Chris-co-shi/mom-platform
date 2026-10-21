package io.github.chrisshi.mom.auth.controller;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.auth.application.UserApplication;
import io.github.chrisshi.mom.auth.application.model.UserView;
import io.github.chrisshi.mom.auth.controller.request.ChangeOwnPasswordRequest;
import io.github.chrisshi.mom.auth.controller.request.UpdateOwnProfileRequest;
import io.github.chrisshi.mom.auth.controller.response.CurrentSessionResponse;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本人资料与认证快照的 HTTP 边界；用户身份始终来自已验证 Token。 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class CurrentUserController {
    private final UserApplication userApplication;

    

    /** 查询实时用户资料与当前 Token 权限快照，不延长 Token。 */
    @GetMapping("/me")
    public Result<CurrentSessionResponse> me(BearerTokenAuthentication authentication) {
        return Result.success(currentSession(userApplication.get(authentication.getName()), authentication));
    }

    /** 修改本人显示名；身份不从请求体取得。 */
    @PutMapping("/me/profile")
    public Result<CurrentSessionResponse> updateProfile(BearerTokenAuthentication authentication,
            @Valid @RequestBody UpdateOwnProfileRequest request) {
        return Result.success(currentSession(userApplication.updateOwnProfile(
                authentication.getName(), request.displayName(), request.version()), authentication));
    }

    /** 确认原密码后修改本人密码，不改变现有 Token 生命周期。 */
    @PutMapping("/change-password/me")
    public Result<CurrentSessionResponse> changePassword(BearerTokenAuthentication authentication,
            @Valid @RequestBody ChangeOwnPasswordRequest request) {
        return Result.success(currentSession(userApplication.changeOwnPassword(
                authentication.getName(), request.currentPassword(), request.newPassword(), request.version()), authentication));
    }

    /** 查询指定用户的角色关系仍由 UserController 负责；此方法只组合本人资料与已验证快照。 */
    private CurrentSessionResponse currentSession(UserView user, BearerTokenAuthentication authentication) {
        return new CurrentSessionResponse(
                new CurrentSessionResponse.UserProfile(user.id(), user.username(), user.displayName(), user.version()),
                new CurrentSessionResponse.Authorization(authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).sorted().toList()),
                new CurrentSessionResponse.Session(authentication.getToken().getExpiresAt()));
    }
}
