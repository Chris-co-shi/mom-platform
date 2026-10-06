package io.github.chrisshi.mom.webmvc.i18n;

import org.springframework.security.core.Authentication;

/**
 * 宿主服务配置的 I18n 管理权限判定。
 *
 * <p>只比较当前 Token authority；没有认证或未配置具体权限时拒绝，不允许管理员角色隐式绕过。
 * Runtime GET 与此管理判定分离，可由宿主 SecurityFilterChain 单独公开。</p>
 */
public final class I18nManagementAccess {
    private final String readAuthority;
    private final String writeAuthority;

    /** @param readAuthority 读取权限码 @param writeAuthority 写入权限码 */
    public I18nManagementAccess(String readAuthority, String writeAuthority) {
        if (readAuthority == null || readAuthority.isBlank()
                || writeAuthority == null || writeAuthority.isBlank()) {
            throw new IllegalArgumentException("I18n 管理权限必须明确配置");
        }
        this.readAuthority = readAuthority;
        this.writeAuthority = writeAuthority;
    }

    /** @return 已认证且拥有读取权限 */
    public boolean canRead(Authentication authentication) {
        return has(authentication, readAuthority);
    }

    /** @return 已认证且拥有写入权限 */
    public boolean canWrite(Authentication authentication) {
        return has(authentication, writeAuthority);
    }

    private static boolean has(Authentication authentication, String authority) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
