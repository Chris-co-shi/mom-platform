package io.github.chrisshi.mom.webmvc.i18n;

import org.springframework.security.core.Authentication;

/** 三个已收敛 Owner 使用统一写权限；未迁移 Owner 暂时沿用显式旧权限配置。 */
public final class I18nManagementAccess {
    private final boolean unified;
    private final String readAuthority;
    private final String writeAuthority;

    /** @param owner 显式 I18n 数据 Owner；未设置时保留兼容权限判定 */
    public I18nManagementAccess(String owner, String readAuthority, String writeAuthority) {
        this.unified = "auth".equals(owner) || "mdm".equals(owner) || "system".equals(owner);
        this.readAuthority = readAuthority;
        this.writeAuthority = writeAuthority;
    }

    /** 三个已收敛 Owner 的读取完全公开；其他 Owner 使用旧读取权限。 */
    public boolean canRead(Authentication authentication) {
        return unified || has(authentication, readAuthority);
    }

    /** 三个已收敛 Owner 写入只接受 system:i18n:write。 */
    public boolean canWrite(Authentication authentication) {
        return has(authentication, unified ? "system:i18n:write" : writeAuthority);
    }

    private static boolean has(Authentication authentication, String authority) {
        return authority != null && !authority.isBlank() && authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
