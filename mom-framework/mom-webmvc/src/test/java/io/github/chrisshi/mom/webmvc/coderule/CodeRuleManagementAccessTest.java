package io.github.chrisshi.mom.webmvc.coderule;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** Owner 权限必须来自启动配置，缺少配置、身份或对应权限均拒绝访问。 */
class CodeRuleManagementAccessTest {
    private static Authentication actor(String authority) {
        return new UsernamePasswordAuthenticationToken("user", "", List.of(new SimpleGrantedAuthority(authority)));
    }
    @Test void eachOwnerRequiresItsOwnReadAndWriteAuthority() {
        var mdm = new CodeRuleManagementAccess("mdm", "mdm:code-rule:read", "mdm:code-rule:write");
        var second = new CodeRuleManagementAccess("test_owner", "test_owner:rules:view", "test_owner:rules:manage");
        assertThat(mdm.canRead(actor("mdm:code-rule:read"))).isTrue();
        assertThat(mdm.canWrite(actor("mdm:code-rule:write"))).isTrue();
        assertThat(second.canRead(actor("test_owner:rules:view"))).isTrue();
        assertThat(second.canWrite(actor("test_owner:rules:manage"))).isTrue();
        assertThat(second.canRead(actor("mdm:code-rule:read"))).isFalse();
        assertThat(second.canWrite(actor("mdm:code-rule:write"))).isFalse();
        assertThat(mdm.canRead(actor("system:code-rule:read"))).isFalse();
        assertThat(mdm.canWrite(actor("system:code-rule:write"))).isFalse();
        assertThat(mdm.canWrite(actor("mdm:code-rule:read"))).isFalse();
    }
    @Test void invalidConfigurationAndMissingIdentityFailClosed() {
        var access = new CodeRuleManagementAccess("mdm", "mdm:code-rule:read", "mdm:code-rule:write");
        assertThat(access.canRead(null)).isFalse();
        assertThat(access.canWrite(UsernamePasswordAuthenticationToken.unauthenticated("user", ""))).isFalse();
        assertThat(access.canRead(new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("mdm:code-rule:read"))))).isFalse();
        for (String value : new String[]{"", " ", "system:code-rule:read", "mdm:*", "mdm:code-rule:read "}) {
            var invalid = new CodeRuleManagementAccess("mdm", value, value);
            assertThat(invalid.canRead(actor(value.isBlank() ? "other" : value))).isFalse();
            assertThat(invalid.canWrite(actor(value.isBlank() ? "other" : value))).isFalse();
        }
        assertThat(new CodeRuleManagementAccess("", "mdm:code-rule:read", "mdm:code-rule:write")
                .canRead(actor("mdm:code-rule:read"))).isFalse();
    }
}
