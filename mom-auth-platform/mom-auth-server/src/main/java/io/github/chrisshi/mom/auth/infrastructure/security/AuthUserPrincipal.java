package io.github.chrisshi.mom.auth.infrastructure.security;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Mini Auth 对 Spring Security {@link UserDetails} 的适配模型。
 *
 * <p>它刻意与 {@code UserEntity} 分离，避免数据库持久化模型直接实现 Spring Security 接口。
 * {@code username} 是登录凭据名称，{@code userId} 才是 MOM 内部稳定身份。</p>
 */
public final class AuthUserPrincipal implements UserDetails, CredentialsContainer {

    private final String userId;
    private final String username;
    private String password;
    private final boolean enabled;
    private final List<SimpleGrantedAuthority> authorities;

    public AuthUserPrincipal(
        String userId,
        String username,
        String password,
        boolean enabled,
        List<String> authorities
    ) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities.stream()
            .distinct()
            .map(SimpleGrantedAuthority::new)
            .toList();
    }

    public String userId() {
        return userId;
    }

    public List<String> authorityValues() {
        return authorities.stream().map(SimpleGrantedAuthority::getAuthority).toList();
    }

    @Override
    public Collection<SimpleGrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        // V1 尚未建设账号过期策略；出现真实业务需求后再增加对应字段与规则。
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // V1 尚未建设登录失败锁定/人工锁定策略。
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // V1 尚未建设密码过期策略。
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        // ProviderManager 默认在认证成功后清除凭据，避免密码摘要在内存中保留更久。
        password = null;
    }
}
