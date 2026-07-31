package io.github.chrisshi.mom.system.web.preference;

import io.github.chrisshi.mom.system.api.ResolvedUserPreference;
import io.github.chrisshi.mom.system.api.UserDensity;
import io.github.chrisshi.mom.system.api.UserThemeMode;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationService;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Preference API 的认证、自助访问、400/409/413、Reset 与 DTO 身份隔离测试。 */
@SpringJUnitWebConfig(SystemUserPreferenceWebSecurityTest.TestWebConfiguration.class)
class SystemUserPreferenceWebSecurityTest {
    private final MockMvc mockMvc;
    private final SystemUserPreferenceApplicationService service;

    @Autowired
    SystemUserPreferenceWebSecurityTest(WebApplicationContext context, SystemUserPreferenceApplicationService service) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        this.service = service;
    }

    @BeforeEach
    void resetMockAndSecurityContext() {
        reset(service);
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedRequestsMustReturn401WhileAnyAuthenticatedUserNeedsNoAdminPermission() throws Exception {
        mockMvc.perform(get("/api/system/preferences/me")).andExpect(status().isUnauthorized());
        when(service.getMyPreference()).thenReturn(defaultPreference());
        mockMvc.perform(get("/api/system/preferences/me").with(jwt().jwt(jwt -> jwt.subject("101"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("zh-CN"));
    }

    @Test
    void authenticatedUserCanSaveAndResetOnlyThroughMeRoutes() throws Exception {
        when(service.saveMyPreference(any())).thenReturn(defaultPreference());
        when(service.resetMyPreference(any())).thenReturn(defaultPreference());
        mockMvc.perform(put("/api/system/preferences/me").with(jwt().jwt(jwt -> jwt.subject("101")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"en-US\",\"displayTimezone\":\"Asia/Tokyo\","
                                + "\"themeMode\":\"DARK\",\"density\":\"COMPACT\","
                                + "\"pageSize\":20,\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/system/preferences/me/reset").with(jwt().jwt(jwt -> jwt.subject("101")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        verify(service).saveMyPreference(any());
        mockMvc.perform(get("/api/system/preferences/202").with(jwt().jwt(jwt -> jwt.subject("101"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/system/preferences/202").with(jwt().jwt(jwt -> jwt.subject("101")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void displayPreferenceMustRejectIdentityAuthorizationAuditAndOrdinaryUnknownFields() throws Exception {
        List<String> undeclaredFields = List.of(
                "userId", "user_id", "username", "subject", "sub", "actorId", "principal",
                "role", "roles", "permission", "permissions", "scope", "scopes", "factoryScope",
                "partyScope", "authorization", "id", "createdBy", "createdAt", "updatedBy", "updatedAt",
                "versionActor", "padding", "debug", "metadata", "extra", "unknownField");

        for (String field : undeclaredFields) {
            mockMvc.perform(put("/api/system/preferences/me").with(jwt().jwt(jwt -> jwt.subject("101")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"locale\":\"en-US\",\"version\":0,\"" + field + "\":\"202\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid_request"))
                    .andExpect(jsonPath("$.message").value("请求参数格式非法"));
        }

        verify(service, never()).saveMyPreference(any());
    }

    @Test
    void resetAndTopLevelViewMustRejectUnknownFieldsBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/system/preferences/me/reset").with(jwt().jwt(jwt -> jwt.subject("101")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"subject\":\"202\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
        mockMvc.perform(put("/api/system/preferences/me/views/mom-admin/iam.users.list")
                        .with(jwt().jwt(jwt -> jwt.subject("101")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schemaVersion\":1,\"columns\":[],\"sorts\":[],\"filters\":[],"
                                + "\"version\":0,\"createdBy\":\"202\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));

        verify(service, never()).resetMyPreference(any());
        verify(service, never()).saveMyView(anyString(), anyString(), any());
    }

    @Test
    void nestedColumnSortAndFilterRequestsMustRejectUnknownFields() throws Exception {
        List<String> bodies = List.of(
                "{\"schemaVersion\":1,\"columns\":[{\"columnKey\":\"display-name\","
                        + "\"visible\":true,\"order\":0,\"pinned\":\"NONE\",\"userId\":\"202\"}],"
                        + "\"sorts\":[],\"filters\":[],\"version\":0}",
                "{\"schemaVersion\":1,\"columns\":[],\"sorts\":[{\"fieldKey\":\"display-name\","
                        + "\"direction\":\"ASC\",\"priority\":0,\"permissions\":[\"admin\"]}],"
                        + "\"filters\":[],\"version\":0}",
                "{\"schemaVersion\":1,\"columns\":[],\"sorts\":[],\"filters\":[{"
                        + "\"fieldKey\":\"display-name\",\"operator\":\"EQ\",\"valueType\":\"STRING\","
                        + "\"values\":[\"alice\"],\"metadata\":{}}],\"version\":0}");

        for (String body : bodies) {
            mockMvc.perform(put("/api/system/preferences/me/views/mom-admin/iam.users.list")
                            .with(jwt().jwt(jwt -> jwt.subject("101")))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid_request"));
        }

        verify(service, never()).saveMyView(anyString(), anyString(), any());
    }

    @Test
    void viewReadWriteListAndResetMustRequireAuthenticationWithoutAdminPermission() throws Exception {
        var view = defaultView();
        when(service.getMyView(anyString(), anyString())).thenReturn(view);
        when(service.saveMyView(anyString(), anyString(), any())).thenReturn(view);
        when(service.resetMyView(anyString(), anyString(), any())).thenReturn(view);
        when(service.listMyViews(anyString())).thenReturn(List.of(view));

        var auth = jwt().jwt(jwt -> jwt.subject("101"));
        mockMvc.perform(get("/api/system/preferences/me/views/mom-admin/iam.users.list").with(auth))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/system/preferences/me/views/mom-admin/iam.users.list")
                        .with(jwt().jwt(jwt -> jwt.subject("101")))
                        .contentType(MediaType.APPLICATION_JSON).content(viewBody()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/system/preferences/me/views/mom-admin/iam.users.list/reset")
                        .with(jwt().jwt(jwt -> jwt.subject("101")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/system/preferences/me/views?applicationCode=mom-admin")
                        .with(jwt().jwt(jwt -> jwt.subject("101"))))
                .andExpect(status().isOk());
    }

    @Test
    void malformedStaleAndOversizedRequestsMustUseStableStatusAndCode() throws Exception {
        mockMvc.perform(put("/api/system/preferences/me").with(jwt().jwt(jwt -> jwt.subject("101")))
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));

        when(service.saveMyPreference(any())).thenThrow(new SystemUserPreferenceException.StaleVersion());
        mockMvc.perform(put("/api/system/preferences/me").with(jwt().jwt(jwt -> jwt.subject("101")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale_version"));

        String oversized = "{\"schemaVersion\":1,\"columns\":[{\"columnKey\":\""
                + "x".repeat(17_000)
                + "\",\"visible\":true,\"order\":0,\"pinned\":\"NONE\"}],"
                + "\"sorts\":[],\"filters\":[],\"version\":0}";
        mockMvc.perform(put("/api/system/preferences/me/views/mom-admin/iam.users.list")
                        .with(jwt().jwt(jwt -> jwt.subject("101")))
                        .contentType(MediaType.APPLICATION_JSON).content(oversized))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"));
    }

    @Test
    void currentUserAdapterMustOnlyAcceptJwtSubject() {
        SecurityContextCurrentPreferenceUserProvider provider = new SecurityContextCurrentPreferenceUserProvider();
        SecurityContextHolder.clearContext();
        org.assertj.core.api.Assertions.assertThatThrownBy(provider::requireUserId)
                .isInstanceOf(SystemUserPreferenceException.NotAuthenticated.class);
        var token = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                .header("alg", "none").subject("101").build();
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                        token, List.of()));
        org.assertj.core.api.Assertions.assertThat(provider.requireUserId()).isEqualTo("101");
        SecurityContextHolder.clearContext();
    }

    @Test
    void selfReportedIdentityHeadersMustNotOverrideJwtSubject() throws Exception {
        when(service.getMyPreference()).thenAnswer(invocation -> {
            var provider = new SecurityContextCurrentPreferenceUserProvider();
            org.assertj.core.api.Assertions.assertThat(provider.requireUserId()).isEqualTo("101");
            return defaultPreference();
        });

        mockMvc.perform(get("/api/system/preferences/me")
                        .header("X-User-Id", "202")
                        .header("X-Subject", "202")
                        .header("X-Actor-Id", "202")
                        .with(jwt().jwt(jwt -> jwt.subject("101"))))
                .andExpect(status().isOk());

        verify(service).getMyPreference();
    }

    private static String viewBody() {
        return "{\"schemaVersion\":1,\"columns\":[{\"columnKey\":\"display-name\","
                + "\"visible\":true,\"order\":0,\"width\":200,\"pinned\":\"NONE\"}],"
                + "\"sorts\":[],\"filters\":[],\"pageSize\":20,\"version\":0}";
    }

    private static ResolvedUserPreference defaultPreference() {
        var source = ResolvedUserPreference.Source.PLATFORM_DEFAULT;
        return new ResolvedUserPreference("zh-CN", "UTC", UserThemeMode.SYSTEM, UserDensity.COMFORTABLE,
                20, 0, false, null, new ResolvedUserPreference.Sources(source, source, source, source, source));
    }

    private static io.github.chrisshi.mom.system.api.UserViewSetting defaultView() {
        return new io.github.chrisshi.mom.system.api.UserViewSetting(
                "mom-admin", "iam.users.list", 1, List.of(), List.of(), List.of(), null,
                false, 0, false, null);
    }

    /** 最小真实 MVC + SecurityFilterChain；保留认证门禁，不访问外部 JWK。 */
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestWebConfiguration {
        @Bean
        SystemUserPreferenceApplicationService preferenceService() {
            return mock(SystemUserPreferenceApplicationService.class);
        }

        @Bean
        SystemUserPreferenceController preferenceController(SystemUserPreferenceApplicationService service) {
            return new SystemUserPreferenceController(service);
        }

        @Bean
        SystemUserPreferenceExceptionHandler preferenceExceptionHandler() {
            return new SystemUserPreferenceExceptionHandler();
        }

        @Bean
        JwtDecoder preferenceJwtDecoder() {
            return token -> { throw new IllegalStateException("测试不调用真实 Decoder"); };
        }

        @Bean
        SecurityFilterChain preferenceSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .oauth2ResourceServer(resource -> resource.jwt(withDefaults()));
            return http.build();
        }
    }
}
