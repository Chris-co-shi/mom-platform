package io.github.chrisshi.mom.iam.web;

import io.github.chrisshi.mom.iam.application.authentication.IamFirstPartyLoginApplicationService;
import io.github.chrisshi.mom.iam.security.IamAccessTokenIssuer;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * MOM 第一方客户端 JSON 认证 Web Adapter。
 *
 * <p>Path、Method、Request/Response DTO 和错误契约保持不变。控制器只做 JSON 绑定、既有输入校验、
 * 请求元数据提取、Application Command 转换和响应映射；账号认证、Client Policy、审计、Session 与
 * 初始 Token 签发由 {@link IamFirstPartyLoginApplicationService} 编排。</p>
 *
 * <p>Refresh 与 Logout 继续直接复用唯一的 Session Rotation/撤销核心，不创建第二套实现。密码只在
 * 同步登录或首次改密调用链中传递，普通业务服务、Gateway 和前端响应均不可获得密码。</p>
 */
@RestController
@RequestMapping("/api/iam/auth")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class IamDirectAuthenticationController {
    private static final Set<String> FIRST_PARTY_SCOPES = Set.of("openid", "profile");

    private final IamFirstPartyLoginApplicationService loginService;
    private final IamSessionTokenService sessions;
    private final IamAccessTokenIssuer tokenIssuer;

    /** 创建保持第一方 JSON 契约的薄 Web Adapter。 */
    public IamDirectAuthenticationController(
            IamFirstPartyLoginApplicationService loginService,
            IamSessionTokenService sessions,
            IamAccessTokenIssuer tokenIssuer) {
        this.loginService = loginService;
        this.sessions = sessions;
        this.tokenIssuer = tokenIssuer;
    }

    /** 校验第一方请求并映射 Application Service 的登录结果。 */
    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest command, HttpServletRequest request) {
        requireLoginCommand(command);
        IamFirstPartyLoginApplicationService.LoginResult result = loginService.login(
                new IamFirstPartyLoginApplicationService.LoginCommand(
                        command.username(), command.password(), command.clientId()),
                metadata(command.deviceName(), request));
        return tokenResponse(result);
    }

    /** 再次校验临时密码并完成首次改密，响应仍使用既有 TokenResponse。 */
    @PostMapping("/password/change-required")
    public TokenResponse changeRequiredPassword(
            @RequestBody RequiredPasswordChangeRequest command,
            HttpServletRequest request) {
        requirePasswordChangeCommand(command);
        IamFirstPartyLoginApplicationService.LoginResult result =
                loginService.changeRequiredPassword(
                        new IamFirstPartyLoginApplicationService.RequiredPasswordChangeCommand(
                                command.username(), command.currentPassword(), command.newPassword(),
                                command.confirmation(), command.clientId()),
                        metadata(command.deviceName(), request));
        return tokenResponse(result);
    }

    /** 消费当前 ACTIVE Refresh Token，执行 Rotation，并返回唯一后继 Token。 */
    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody RefreshRequest command) {
        if (command == null || blank(command.clientId()) || blank(command.refreshToken())) {
            throw new InvalidAuthenticationRequestException("Refresh 请求不完整");
        }
        IamSessionTokenService.Rotation rotation = sessions.rotate(
                command.refreshToken(), command.clientId());
        IamAccessTokenIssuer.IssuedAccessToken accessToken = tokenIssuer.issue(
                rotation.authorization(),
                rotation.sessionId(),
                command.clientId(),
                rotation.issuedAt(),
                rotation.accessExpiresAt(),
                FIRST_PARTY_SCOPES);
        return tokenResponse(
                accessToken.tokenValue(),
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

    private static TokenResponse tokenResponse(
            IamFirstPartyLoginApplicationService.LoginResult result) {
        return tokenResponse(
                result.accessToken(),
                result.refreshToken(),
                result.sessionId(),
                result.issuedAt(),
                result.accessExpiresAt(),
                result.sessionExpiresAt());
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

    private static IamFirstPartyLoginApplicationService.RequestMetadata metadata(
            String deviceName,
            HttpServletRequest request) {
        return new IamFirstPartyLoginApplicationService.RequestMetadata(
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                normalizeDeviceName(deviceName));
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

    /** 第一方登录请求；字段名和类型属于既有公开契约。 */
    public record LoginRequest(
            String username,
            String password,
            String clientId,
            String deviceName) {
    }

    /** 第一方首次改密请求；与普通登录请求保持语义隔离。 */
    public record RequiredPasswordChangeRequest(
            String username,
            String currentPassword,
            String newPassword,
            String confirmation,
            String clientId,
            String deviceName) {
    }

    /** 第一方 Refresh 请求；继续复用 Session Rotation。 */
    public record RefreshRequest(String clientId, String refreshToken) {
    }

    /** 第一方 Token 响应；不用于 OAuth2/OIDC 标准 Token Endpoint。 */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            String sessionId,
            Instant accessExpiresAt,
            Instant sessionExpiresAt) {
    }

    /** 第一方 Logout 响应。 */
    public record LogoutResponse(boolean revoked) {
    }

    /** Web Adapter 的请求结构错误；不得包含密码或 Token 原文。 */
    public static final class InvalidAuthenticationRequestException extends RuntimeException {
        public InvalidAuthenticationRequestException(String message) {
            super(message);
        }
    }
}
