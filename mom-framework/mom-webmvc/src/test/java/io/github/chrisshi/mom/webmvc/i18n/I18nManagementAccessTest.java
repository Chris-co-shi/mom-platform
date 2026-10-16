package io.github.chrisshi.mom.webmvc.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class I18nManagementAccessTest {
    @Test
    void configuredOwnersExposeReadsButRequireTheUnifiedWritePermission() {
        var access = new I18nManagementAccess("mdm", "mdm:i18n:read", "mdm:i18n:write");
        var legacy = authenticated("mdm:i18n:write");
        var writer = authenticated("system:i18n:write");
        assertThat(access.canRead(null)).isTrue();
        assertThat(access.canWrite(legacy)).isFalse();
        assertThat(access.canWrite(writer)).isTrue();
    }

    @Test
    void ownerWithoutMigrationSettingsRetainsItsExistingPermissionContract() {
        var access = new I18nManagementAccess("", "qms:i18n:read", "qms:i18n:write");
        assertThat(access.canRead(null)).isFalse();
        assertThat(access.canRead(authenticated("qms:i18n:read"))).isTrue();
        assertThat(access.canWrite(authenticated("qms:i18n:write"))).isTrue();
        assertThat(access.canWrite(authenticated("system:i18n:write"))).isFalse();
    }

    private static UsernamePasswordAuthenticationToken authenticated(String authority) {
        return UsernamePasswordAuthenticationToken.authenticated("test", "n/a",
                List.of(new SimpleGrantedAuthority(authority)));
    }
}
