package io.github.chrisshi.mom.iam.application.authentication;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.core.security.ActorType;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.security.IamAccessTokenIssuer;
import io.github.chrisshi.mom.iam.security.IamAccountAuthenticationService;
import io.github.chrisshi.mom.iam.security.IamClientAccessPolicyService;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;

import java.time.Instant;
import java.util.Set;
import java.util.function.Supplier;

/**
 * IAM 第一方 JSON 登录与首次改密的协议无关应用编排。
 *
 * <p>该服务只接收内部 Command 和请求元数据，依次执行凭据认证、Client Policy、审计上下文、账号
 * 状态、权威 Session 与统一 Access Token 签发。它不依赖 Servlet、Controller DTO、OAuth2 HTTP
 * Request、Gateway 或前端工程；事务、行锁、Refresh 持久化和撤销仍由既有权威服务负责。</p>
 *
 * <p>任何认证、存储或签名失败均向上传播并失败关闭，不创建第二套 Session、Token 或 Claims 核心。</p>
 */
public final class IamFirstPartyLoginApplicationService {
    private static final Set<String> FIRST_PARTY_SCOPES = Set.of("openid", "profile");

    private final AuthenticationProvider authenticationProvider;
    private final IamAccountAuthenticationService accounts;
    private final IamClientAccessPolicyService clientAccess;
    private final IamSessionTokenService sessions;
    private final IamAccessTokenIssuer tokenIssuer;
    private final AuditContextExecutor auditContextExecutor;

    /** 创建复用 IAM 既有认证、Session 和签名能力的第一方登录应用服务。 */
    public IamFirstPartyLoginApplicationService(
            AuthenticationProvider authenticationProvider,
            IamAccountAuthenticationService accounts,
            IamClientAccessPolicyService clientAccess,
            IamSessionTokenService sessions,
            IamAccessTokenIssuer tokenIssuer,
            AuditContextExecutor auditContextExecutor) {
        this.authenticationProvider = authenticationProvider;
        this.accounts = accounts;
        this.clientAccess = clientAccess;
        this.sessions = sessions;
        this.tokenIssuer = tokenIssuer;
        this.auditContextExecutor = auditContextExecutor;
    }

    /**
     * 执行第一方登录；账号要求首次改密时不创建 Session 或 Token。
     *
     * @param command 已通过 Web 必填校验的内部登录命令
     * @param metadata 受服务端控制的请求来源元数据
     * @return 协议无关的 Token 与 Session 结果
     * @throws InvalidCredentialsException 凭据错误、账号锁定或禁用
     * @throws PasswordChangeRequiredException 账号必须先修改临时密码
     */
    public LoginResult login(LoginCommand command, RequestMetadata metadata) {
        authenticate(command.username(), command.password());
        clientAccess.requireAuthorization(command.username(), command.clientId());
        return runAsAuthenticatedUser(command.username(), command.clientId(), () -> {
            accounts.recordSuccessfulLogin(command.username());
            if (accounts.requiresPasswordChange(command.username())) {
                throw new PasswordChangeRequiredException();
            }
            return issue(command.username(), command.clientId(), metadata);
        });
    }

    /**
     * 再次认证临时密码并完成首次改密，然后复用同一 Session/Token 签发路径。
     *
     * @param command 已通过 Web 必填校验的首次改密命令
     * @param metadata 受服务端控制的请求来源元数据
     * @return 协议无关的 Token 与 Session 结果
     * @throws PasswordChangeNotRequiredException 账号当前不需要首次改密
     */
    public LoginResult changeRequiredPassword(
            RequiredPasswordChangeCommand command,
            RequestMetadata metadata) {
        authenticate(command.username(), command.currentPassword());
        clientAccess.requireAuthorization(command.username(), command.clientId());
        return runAsAuthenticatedUser(command.username(), command.clientId(), () -> {
            if (!accounts.requiresPasswordChange(command.username())) {
                throw new PasswordChangeNotRequiredException();
            }
            accounts.changeRequiredPassword(
                    command.username(), command.newPassword(), command.confirmation());
            accounts.recordSuccessfulLogin(command.username());
            return issue(command.username(), command.clientId(), metadata);
        });
    }

    private LoginResult issue(String username, String clientId, RequestMetadata metadata) {
        IamSessionTokenService.InitialIssue initial = sessions.issueInitial(
                username,
                clientId,
                metadata.ipAddress(),
                metadata.userAgent(),
                metadata.deviceName());
        IamAccessTokenIssuer.IssuedAccessToken accessToken = tokenIssuer.issue(
                initial.authorization(),
                initial.sessionId(),
                clientId,
                initial.issuedAt(),
                initial.accessExpiresAt(),
                FIRST_PARTY_SCOPES);
        return new LoginResult(
                accessToken.tokenValue(),
                initial.refreshToken(),
                initial.sessionId(),
                accessToken.issuedAt(),
                accessToken.expiresAt(),
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

    private <T> T runAsAuthenticatedUser(String username, String clientId, Supplier<T> action) {
        IamAccountAuthenticationService.AuditIdentity user =
                accounts.requireAuditIdentity(username);
        AuditActor actor = new AuditActor(
                user.userId(),
                ActorType.USER,
                user.userType().name(),
                clientId,
                null,
                CorrelationContext.currentId());
        return auditContextExecutor.runAsActor(actor, action);
    }

    /** 第一方登录内部命令；密码仅在当前同步调用链中传递。 */
    public record LoginCommand(String username, String password, String clientId) {
    }

    /** 首次改密内部命令；不复用任何 Web Request DTO。 */
    public record RequiredPasswordChangeCommand(
            String username,
            String currentPassword,
            String newPassword,
            String confirmation,
            String clientId) {
    }

    /** 由 Web Adapter 从可信 Servlet 请求提取的连接和设备元数据。 */
    public record RequestMetadata(
            String ipAddress,
            String userAgent,
            String deviceName) {
    }

    /** 第一方 Web Adapter 映射为既有 JSON Response 的内部结果。 */
    public record LoginResult(
            String accessToken,
            String refreshToken,
            String sessionId,
            Instant issuedAt,
            Instant accessExpiresAt,
            Instant sessionExpiresAt) {
    }

    /** 凭据或账号状态不可用；消息刻意保持泛化，避免账号枚举。 */
    public static final class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("账号或密码错误，或账号当前不可用");
        }
    }

    /** 当前账号必须先修改临时密码。 */
    public static final class PasswordChangeRequiredException extends RuntimeException {
        public PasswordChangeRequiredException() {
            super("当前账号必须先修改临时密码");
        }
    }

    /** 当前账号不允许调用首次改密用例。 */
    public static final class PasswordChangeNotRequiredException extends RuntimeException {
        public PasswordChangeNotRequiredException() {
            super("当前账号不需要首次改密");
        }
    }
}
