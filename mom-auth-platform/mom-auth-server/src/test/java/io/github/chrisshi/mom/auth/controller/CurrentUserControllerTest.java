package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.AuthenticationApplication;
import io.github.chrisshi.mom.auth.application.AuthErrorCode;
import io.github.chrisshi.mom.auth.application.AuthException;
import io.github.chrisshi.mom.auth.application.UserApplication;
import io.github.chrisshi.mom.auth.application.model.UserView;
import io.github.chrisshi.mom.auth.infrastructure.configuration.AuthExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * 自助 HTTP 边界测试，验证可信主体映射、输入白名单、校验和稳定错误码。
 * 使用隔离 MockMvc，不连接数据库或 Redis，也不宣称覆盖完整认证过滤链。
 */
class CurrentUserControllerTest {
    private final UserApplication users = mock(UserApplication.class);
    private final BearerTokenAuthentication authentication = mock(BearerTokenAuthentication.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(authentication.getName()).thenReturn("self");
        when(authentication.getAuthorities()).thenReturn(List.of());
        when(authentication.getToken()).thenReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
            "test-only", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2099-01-01T00:00:00Z")));
        mvc = standaloneSetup(new AuthenticationController(mock(AuthenticationApplication.class), users))
            .setControllerAdvice(new AuthExceptionHandler()).build();
    }

    @Test
    void profileMustUsePrincipalAndIgnoreAdministrativeFields() throws Exception {
        when(users.updateOwnProfile("self", "My name", 3L)).thenReturn(
            new UserView("self", "operator", "My name", true, 4L, null, null));
        mvc.perform(put("/me/profile").principal(authentication).contentType(MediaType.APPLICATION_JSON).content("""
            {"userId":"victim","username":"admin","enabled":true,"roles":["ADMIN"],"displayName":"My name","version":3}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.userId").value("self"))
            .andExpect(jsonPath("$.data.user.username").value("operator"))
            .andExpect(jsonPath("$.data.user.version").value(4))
            .andExpect(jsonPath("$.data.authorization.authorities").isEmpty())
            .andExpect(jsonPath("$.data.user.enabled").doesNotExist())
            .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
        verify(users).updateOwnProfile("self", "My name", 3L);
        verifyNoMoreInteractions(users);
    }

    @Test
    void passwordMustUsePrincipalAndKeepBadPasswordDistinctFromExpiredSession() throws Exception {
        when(users.changeOwnPassword("self", " wrong ", " new password ", 3L))
            .thenThrow(new AuthException(AuthErrorCode.CURRENT_PASSWORD_INVALID));
        mvc.perform(put("/me/password").principal(authentication).contentType(MediaType.APPLICATION_JSON).content("""
            {"userId":"victim","currentPassword":" wrong ","newPassword":" new password ","version":3}
            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("auth.current_password_invalid"));
        verify(users).changeOwnPassword("self", " wrong ", " new password ", 3L);
    }

    @Test
    void invalidFieldsMustFailBeforeApplication() throws Exception {
        mvc.perform(put("/me/profile").principal(authentication).contentType(MediaType.APPLICATION_JSON)
            .content("{\"displayName\":\"  \",\"version\":-1}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/me/password").principal(authentication).contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"\",\"newPassword\":\"short\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(users);
    }

    @Test
    void versionConflictMustRemain409() throws Exception {
        when(users.updateOwnProfile(any(), any(), anyLong())).thenThrow(new AuthException(AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        mvc.perform(put("/me/profile").principal(authentication).contentType(MediaType.APPLICATION_JSON)
            .content("{\"displayName\":\"Name\",\"version\":0}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("auth.optimistic_lock_conflict"));
    }
}
