package io.github.chrisshi.mom.iam.web;

import io.github.chrisshi.mom.iam.security.IamAccountAuthenticationService;
import io.github.chrisshi.mom.iam.security.IamClientAccessPolicyService;
import io.github.chrisshi.mom.iam.security.IamSessionJwtIssuer;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * MOM 第一方客户端 JSON 认证入口。
 *
 * <p>该控制器不替代 Spring Authorization Server 的标准 {@code /oauth2/**} 协议端点，而是为
 * MOM Admin、供应商门户、客户门户和移动端提供由各自前端承载登录界面的第一方契约。账号校验、
 * Client/user_type/Party/Mobile Access 入口策略、Session、Opaque Refresh Rotation、JWT 与撤销存储
 * 全部复用 P1.5 既有权威服务，不创建第二套认证状态。</p>
 *
 * <p>密码只允许发送到本控制器的登录与首次改密端点；响应和异常均不得回显密码、摘要或 Refresh
 * Token。Access Token 与 Refresh Token 的客户端存储策略由各端运行时负责。</p>
 */
@RestController
@RequestMapping("/api/iam/auth")
public final class IamDirectAuthenticationController {
    private static final Set<String> FIRST_PARTY_SCOPES = Set.of("openid", "profile");

    private final AuthenticationProvider authenticationProvider;
    private final IamAccountAuthenticationService accounts;
    private final IamClientAccessPolicyService clientAccess;
    private final IamSessionTokenService sessions;
    private final IamSessionJwtIssuer jwtIssuer;

    public IamDirectAuthenticationController(
            AuthenticationProvider authenticationProvider,
            IamAccountAuthenticationService accounts,
            IamClientAccessPolicyService clientAccess,
            IamSessionTokenService sessions,
            IamSessionJwtIssuer jwtIssuer) {
        this.authenticationProvider = authenticationProvider;
        this.accounts = accounts;
        this.clientAccess = clientAccess;
        this.sessions = sessions;
        this.jwtIssuer = jwtIssuer;
    }

    /** 校验第一方账号与应用入口策略，并签发权威 Session、JWT Access Token 和 Opaque Refresh Token。 */
    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest command, HttpServletRequest request) {
        requireLoginCommand(command);
        authenticate(command.username(), command.password());
        clientAccess.requireAuthorization(command.username(), command.clientId());
        accounts.recordSuccessfulLogin(command.username());
        if (accounts.requiresPasswordChange(command.username())) {
            throw new PasswordChangeRequiredException();
        }
        return issue(command.username(), command.clientId(), command.deviceName(), request);
    }

    /**
     * 使用当前临时密码完成首次改密，并在同一交互中签发新的第一方 Session。
     *
     * <p>必须再次校验当前密码，不能仅凭用户名修改凭证。修改密码前继续执行 Client 入口策略，避免
     * 外部账号借由错误 Portal 完成登录。</p>
     */
    @PostMapping("/password/change-required")
    public TokenResponse changeRequiredPassword(
            @RequestBody RequiredPasswordChangeRequest command,
            HttpServletRequest request) {
        requirePasswordChangeCommand(command);
        authenticate(command.username(), command.currentPassword());
        clientAccess.requireAuthorization(command.username(), command.clientId());
        if (!accounts.requiresPasswordChange(command.username())) {
            throw new PasswordChangeNotRequiredException();
        }
        accounts.changeRequiredPassword(
                command.username(), command.newPassword(), command.confirmation());
        accounts.recordSuccessfulLogin(command.username());
        return issue(command.username(), command.clientId(), command.deviceName(), request);
    }

    /** 消费当前 ACTIVE Refresh Token，执行 Rotation，并返回唯一后继 Token。 */
    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody RefreshRequest command) {
        if (command == null || blank(command.clientId()) || blank(command.refreshToken())) {
            throw new InvalidAuthenticationRequestException("Refresh 请求不完整");
        }
        IamSessionTokenService.Rotation rotation = sessions.rotate(
                command.refreshToken(), command.clientId());
        var accessToken = jwtIssuer.issue(
                rotation.authorization(),
                rotation.sessionId(),
                command.clientId(),
                rotation.issuedAt(),
                rotation.accessExpiresAt(),
                FIRST_PARTY_SCOPES);
        return tokenResponse(
                accessToken.getTokenValue(),
                rotation.refreshToken(),
                rotation.sessionId(),
                rotation.issuedAt(),
                rotation.accessExpiresAt(),
                rotation.absoluteExpiresAt());
    }

    /** 由当前 JWT 的 sid 撤销完整 Session；登出不是仅删除浏览器 Token。 */
    @PostMapping("/logout")
    public LogoutResponse logout(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new InvalidAuthenticationRequestException("缺少有效 Access Token");
        }
        String sessionId = jwtAuthentication.getToken().getClaimAsString("sid");
        if (blank(sessionId)) {
            throw new InvalidAuthenticationRequestException("Access Token 缺少 sid");
        }
        sessions.revoke(
                sessionId,
                jwtAuthentication.getToken().getSubject(),
                "self_logout");
        return new LogoutResponse(true);
    }

    private TokenResponse issue(
            String username,
            String clientId,
            String deviceName,
            HttpServletRequest request) {
        IamSessionTokenService.InitialIssue initial = sessions.issueInitial(
                username,
                clientId,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                normalizeDeviceName(deviceName));
        var accessToken = jwtIssuer.issue(
                initial.authorization(),
                initial.sessionId(),
                clientId,
                initial.issuedAt(),
                initial.accessExpiresAt(),
                FIRST_PARTY_SCOPES);
        return tokenResponse(
                accessToken.getTokenValue(),
                initial.refreshToken(),
                initial.sessionId(),
                initial.issuedAt(),
                initial.accessExpiresAt(),
                initial.absoluteExpiresAt());
    }

    private void authenticate(String username, String password) {
        try {
            authenticationProvider.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        }
        catch (AuthenticationException exception) {
            if (exception instanceof BadCredentialsException) {
                accounts.recordBadCredentials(username);
            }
            throw new InvalidCredentialsException();
        }
    }

    private static TokenResponse tokenResponse(
            String accessToken,
            String refreshToken,
            String sessionId,
            Instant issuedAt,
            Instant accessExpiresAt,
            Instant sessionExpiresAt) {
        long expiresIn = Math.max(0L, Duration.between(issuedAt, accessExpiresAt).toSeconds());
        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresIn,
                sessionId,
                accessExpiresAt,
                sessionExpiresAt);
    }

    private static void requireLoginCommand(LoginRequest command) {
        if (command == null
                || blank(command.username())
                || blank(command.password())
                || blank(command.clientId())) {
            throw new InvalidAuthenticationRequestException("登录请求不完整");
        }
    }

    private static void requirePasswordChangeCommand(RequiredPasswordChangeRequest command) {
        if (command == null
                || blank(command.username())
                || blank(command.currentPassword())
                || blank(command.newPassword())
                || blank(command.confirmation())
                || blank(command.clientId())) {
            throw new InvalidAuthenticationRequestException("首次改密请求不完整");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeDeviceName(String value) {
        if (blank(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }

    public record LoginRequest(
            String username,
            String password,
            String clientId,
            String deviceName) {
    }

    public record RequiredPasswordChangeRequest(
            String username,
            String currentPassword,
            String newPassword,
            String confirmation,
            String clientId,
            String deviceName) {
    }

    public record RefreshRequest(String clientId, String refreshToken) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            String sessionId,
            Instant accessExpiresAt,
            Instant sessionExpiresAt) {
    }

    public record LogoutResponse(boolean revoked) {
    }

    public static final class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("账号或密码错误，或账号当前不可用");
        }
    }

    public static final class PasswordChangeRequiredException extends RuntimeException {
        public PasswordChangeRequiredException() {
            super("当前账号必须先修改临时密码");
        }
    }

    public static final class PasswordChangeNotRequiredException extends RuntimeException {
        public PasswordChangeNotRequiredException() {
            super("当前账号不需要首次改密");
        }
    }

    public static final class InvalidAuthenticationRequestException extends RuntimeException {
        public InvalidAuthenticationRequestException(String message) {
            super(message);
        }
    }
}
