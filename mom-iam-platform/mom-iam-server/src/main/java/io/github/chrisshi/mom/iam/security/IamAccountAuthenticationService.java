package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;

/**
 * IAM 数据库账号认证服务。
 *
 * <p>只向 Spring Security 返回标准 {@link User} Principal，避免把密码摘要或持久化实体放入 Session 和
 * OAuth Authorization attributes。账号不存在与密码错误均由 DaoAuthenticationProvider 对外表现为通用失败。</p>
 */
public final class IamAccountAuthenticationService implements UserDetailsService {
    private final IamUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final IamAuthorizationProperties properties;
    private final Clock clock;

    public IamAccountAuthenticationService(
            IamUserRepository users,
            PasswordEncoder passwordEncoder,
            IamAuthorizationProperties properties,
            Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = normalizeUsername(username);
        Instant now = clock.instant();
        users.clearExpiredLock(normalized, now);
        IamUserEntity user = users.findByUsername(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities("ROLE_IAM_USER")
                .disabled(user.getStatus() != IamRecordStatus.ENABLED)
                .accountLocked(locked)
                .build();
    }

    /** 仅对真实存在且可尝试认证的启用账号累计通用凭证失败。 */
    public void recordBadCredentials(String username) {
        users.recordLoginFailure(
                normalizeUsername(username),
                properties.getSecurity().getMaxFailedAttempts(),
                properties.getSecurity().getLockDuration(),
                clock.instant());
    }

    /** 登录成功后清除失败计数并维护 last_login_at。 */
    public IamUserEntity recordSuccessfulLogin(String username) {
        String normalized = normalizeUsername(username);
        users.recordLoginSuccess(normalized, clock.instant());
        return requireUser(normalized);
    }

    /** @return 当前账号是否仍要求首次修改密码 */
    public boolean requiresPasswordChange(String username) {
        return Boolean.TRUE.equals(requireUser(normalizeUsername(username)).getPasswordChangeRequired());
    }

    /**
     * 校验并完成首次改密。错误信息不得包含旧密码摘要或新密码内容。
     */
    public void changeRequiredPassword(String username, String newPassword, String confirmation) {
        IamUserEntity user = requireUser(normalizeUsername(username));
        if (!Boolean.TRUE.equals(user.getPasswordChangeRequired())) {
            throw new IllegalStateException("当前账号不需要执行首次改密");
        }
        if (newPassword == null || newPassword.length() < properties.getSecurity().getMinimumPasswordLength()
                || newPassword.length() > 128) {
            throw new IllegalArgumentException("新密码长度不符合安全要求");
        }
        if (!newPassword.equals(confirmation)) {
            throw new IllegalArgumentException("两次输入的新密码不一致");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        String encoded = passwordEncoder.encode(newPassword);
        if (!users.changePassword(user.getUsername(), encoded, clock.instant())) {
            throw new IllegalStateException("首次改密状态更新失败");
        }
    }

    /** 按认证用户名加载持久化账号，供 Client Policy 与 JWT 基础 Claims 使用。 */
    public IamUserEntity requireUser(String username) {
        return users.findByUsername(normalizeUsername(username))
                .orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return "";
        }
        return username.trim();
    }
}
