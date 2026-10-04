package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.AuthenticationApplication;
import io.github.chrisshi.mom.auth.application.UserApplication;
import io.github.chrisshi.mom.auth.application.model.UserView;
import io.github.chrisshi.mom.auth.controller.response.CurrentSessionResponse;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mini Auth 当前会话 Controller 的快速单元测试。
 *
 * <p>测试只验证已认证 Authentication 到 HTTP Response 的映射，不启动 Redis、数据库或完整 Security
 * Filter Chain。Token 有效性和未认证请求拒绝分别由 mom-security 与真实 Gateway 联调证据覆盖。</p>
 */
class AuthenticationControllerTest {

    /** 当前会话必须返回 Token 身份，并以稳定顺序输出权限快照。 */
    @Test
    void shouldReturnAuthenticatedTokenSnapshot() {
        AuthenticationApplication application = mock(AuthenticationApplication.class);
        UserApplication users = mock(UserApplication.class);
        BearerTokenAuthentication authentication = mock(BearerTokenAuthentication.class);
        OAuth2AccessToken token = mock(OAuth2AccessToken.class);
        Instant expiresAt = Instant.parse("2099-01-01T00:00:00Z");
        when(authentication.getName()).thenReturn("1900000000000000001");
        when(users.get("1900000000000000001")).thenReturn(new UserView(
            "1900000000000000001", "admin", "平台管理员", true, 3L, null, null));
        when(authentication.getAuthorities()).thenReturn(List.of(
            new SimpleGrantedAuthority("auth:user:write"),
            new SimpleGrantedAuthority("auth:user:read")
        ));
        when(authentication.getToken()).thenReturn(token);
        when(token.getExpiresAt()).thenReturn(expiresAt);

        Result<CurrentSessionResponse> result = new AuthenticationController(application, users).me(authentication);

        assertThat(result.code()).isEqualTo(Result.SUCCESS_CODE);
        assertThat(result.data()).isEqualTo(new CurrentSessionResponse(
            new CurrentSessionResponse.UserProfile("1900000000000000001", "admin", "平台管理员", 3L),
            new CurrentSessionResponse.Authorization(List.of("auth:user:read", "auth:user:write")),
            new CurrentSessionResponse.Session(expiresAt)
        ));
    }
}
