package io.github.chrisshi.mom.webmvc.coderule;

import io.github.chrisshi.mom.coderule.CodeRuleOwner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/** 使用服务启动配置中的 Owner 独立管理权限，拒绝跨 Owner 权限及匿名身份。 */
public final class CodeRuleManagementAccess {
    private final String readAuthority;
    private final String writeAuthority;

    /** 未配置或无效权限保留为拒绝状态，不从请求推导权限。 */
    public CodeRuleManagementAccess(String owner, String readAuthority, String writeAuthority) {
        this.readAuthority = valid(owner, readAuthority) ? readAuthority : null;
        this.writeAuthority = valid(owner, writeAuthority) ? writeAuthority : null;
    }

    /** 当前身份必须具有本 Owner 的显式读取权限。 */
    public boolean canRead(Authentication authentication) {
        return has(authentication, readAuthority);
    }

    /** 当前身份必须具有本 Owner 的显式写入权限。 */
    public boolean canWrite(Authentication authentication) {
        return has(authentication, writeAuthority);
    }

    private static boolean valid(String owner, String authority) {
        try { new CodeRuleOwner(owner); }
        catch (IllegalArgumentException exception) { return false; }
        return authority != null && authority.matches("[a-z][a-z0-9_-]*:[a-z][a-z0-9_-]*:[a-z][a-z0-9_-]*")
                && authority.startsWith(owner + ":");
    }

    private static boolean has(Authentication authentication, String authority) {
        return authority != null && authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
