package io.github.chrisshi.mom.system.web.i18n;

import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.MessageView;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.PageView;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.PublishView;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.ResourceView;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.RuntimeView;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationService;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dynamic I18n Runtime 与管理 Permission Reference 的真实 MVC SecurityFilterChain 测试。
 *
 * <p>测试只用 jwt() 建立身份，不关闭过滤器、不连接 JWK/Redis/PostgreSQL。Mock 仅隔离 HTTP Adapter；
 * PostgreSQL 事务与约束由独立 IT 验证。</p>
 */
class SystemI18nWebSecurityTest {
    private static final String CHECKSUM = "a".repeat(64);
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private SystemI18nApplicationService service;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfiguration.class);
        context.refresh();
        service = context.getBean(SystemI18nApplicationService.class);
        reset(service);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void runtimeMustRequireAuthenticationButNoAdminPermission() throws Exception {
        mockMvc.perform(get(runtimePath()).param("locale", "zh-CN"))
                .andExpect(status().isUnauthorized());
        when(service.runtime("mom-web", "common", "zh-CN")).thenReturn(runtime());
        mockMvc.perform(get(runtimePath()).param("locale", "zh-CN").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + CHECKSUM + "\""))
                .andExpect(jsonPath("$.applicationCode").value("mom-web"))
                .andExpect(jsonPath("$.messages.hello").value("你好"))
                .andExpect(jsonPath("$.resourceId").doesNotExist())
                .andExpect(jsonPath("$.publishedBy").doesNotExist());
    }

    @Test
    void matchingEtagMustReturn304WithoutBody() throws Exception {
        when(service.runtime("mom-web", "common", "zh-CN")).thenReturn(runtime());
        mockMvc.perform(get(runtimePath()).param("locale", "zh-CN")
                        .header("If-None-Match", "\"" + CHECKSUM + "\"").with(jwt()))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "\"" + CHECKSUM + "\""))
                .andExpect(content().string(""));
    }

    @Test
    void adminMustSeparateReadWriteAndPublishPermissions() throws Exception {
        when(service.getResource("1")).thenReturn(resource());
        when(service.createResource(any())).thenReturn(resource());
        when(service.publish(anyString(), any())).thenReturn(publish());

        mockMvc.perform(get("/api/system/admin/i18n/resources/1").with(jwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/system/admin/i18n/resources/1").with(readJwt()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/system/admin/i18n/resources").with(readJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(createResourceBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/system/admin/i18n/resources").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(createResourceBody()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/system/admin/i18n/resources/1/publish").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"changeNote\":\"initial\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/system/admin/i18n/resources/1/publish").with(publishJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"changeNote\":\"initial\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void errorsMustMapTo400404And409WithoutDisablingSecurity() throws Exception {
        mockMvc.perform(post("/api/system/admin/i18n/resources").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
        when(service.getResource("missing")).thenThrow(new SystemI18nException.NotFound("不存在"));
        mockMvc.perform(get("/api/system/admin/i18n/resources/missing").with(readJwt()))
                .andExpect(status().isNotFound());
        when(service.changeResourceStatus(anyString(), any()))
                .thenThrow(new SystemI18nException.StaleVersion("冲突"));
        mockMvc.perform(patch("/api/system/admin/i18n/resources/1/status").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stale_version"));
    }

    private static String runtimePath() {
        return "/api/system/i18n/applications/mom-web/resources/common";
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor readJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("system:i18n:read"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writeJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("system:i18n:write"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor publishJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("system:i18n:publish"));
    }

    private static String createResourceBody() {
        return "{\"applicationCode\":\"mom-web\",\"resourceCode\":\"common\","
                + "\"resourceName\":\"Common\",\"defaultLocale\":\"zh-CN\",\"enabled\":true}";
    }

    private static RuntimeView runtime() {
        return new RuntimeView("mom-web", "common", "zh-CN", "zh-CN", 1L, CHECKSUM, 0,
                Instant.parse("2026-07-30T00:00:00Z"), Map.of("hello", "你好"));
    }

    private static ResourceView resource() {
        Instant now = Instant.parse("2026-07-30T00:00:00Z");
        return new ResourceView("1", "mom-web", "common", "Common", "zh-CN", true,
                null, null, null, 0L, null, "actor", now, "actor", now);
    }

    private static PublishView publish() {
        return new PublishView(1L, null, "actor", Instant.parse("2026-07-30T00:00:00Z"),
                Map.of("zh-CN", CHECKSUM, "en-US", CHECKSUM));
    }

    /** 最小真实 MVC、Method Security 与 Resource Server 配置。 */
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestWebConfiguration {
        @Bean
        SystemI18nApplicationService systemI18nApplicationService() {
            return mock(SystemI18nApplicationService.class);
        }

        @Bean
        SystemI18nController systemI18nController(SystemI18nApplicationService service) {
            return new SystemI18nController(service);
        }

        @Bean
        SystemI18nExceptionHandler systemI18nExceptionHandler() {
            return new SystemI18nExceptionHandler();
        }

        @Bean
        JwtDecoder i18nJwtDecoder() {
            return token -> {
                throw new IllegalStateException("Web 安全测试不得解码真实 Token");
            };
        }

        @Bean
        SecurityFilterChain i18nSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .oauth2ResourceServer(resource -> resource.jwt(withDefaults()));
            return http.build();
        }
    }
}
