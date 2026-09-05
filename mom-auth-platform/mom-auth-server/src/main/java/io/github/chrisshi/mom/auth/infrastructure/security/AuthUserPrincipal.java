package io.github.chrisshi.mom.auth.infrastructure.security;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Mini Auth 对 Spring Security {@link UserDetails} 的独立适配模型。
 *
 * <p>该类型刻意与 UserEntity 分离：数据库行模型不实现 Spring Security 接口，避免持久化层被认证框架
 * 反向污染。username 是登录凭据名称，userId 才是 MOM 内部稳定身份。</p>
 *
 * <p>实现 {@link CredentialsContainer} 是为了让 ProviderManager 在成功认证后擦除密码摘要，
 * 减少凭据材料在内存中的保留时间。</p>
 */
public final class AuthUserPrincipal implements UserDetails, CredentialsContainer {

    private final String userId;
    private final String username;
    private String password;
    private final boolean enabled;
    private final List<SimpleGrantedAuthority> authorities;

    /**
     * 构造认证阶段使用的 Principal。
     *
     * @param userId MOM 稳定用户主键
     * @param username 登录名称
     * @param password 数据库中的密码摘要，仅认证阶段使用
     * @param enabled 当前账号是否允许登录
     * @param authorities ROLE_* 与 Permission code 的最终 authority 集合
     */
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

    /**
     * 返回 MOM 稳定用户身份。
     *
     * @return 用户主键，而不是登录名
     */
    public String userId() {
        return userId;
    }

    /**
     * 提取写入 Opaque Token 快照的字符串 authority。
     *
     * @return 去重后的 ROLE_* 与 Permission code
     */
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
        // V1 尚未建设登录失败锁定/人工锁定策略，不提前增加 locked/loginFailureCount 字段。
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // V1 尚未建设密码过期策略，不提前增加 passwordExpired 字段。
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        // ProviderManager 默认在认证成功后调用，避免密码摘要在 Principal 中保留更久。
        password = null;
    }
}
