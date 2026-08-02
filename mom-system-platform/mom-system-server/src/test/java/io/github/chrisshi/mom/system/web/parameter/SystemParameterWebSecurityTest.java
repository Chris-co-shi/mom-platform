package io.github.chrisshi.mom.system.web.parameter;

import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ParameterValueType;
import io.github.chrisshi.mom.system.api.ResolvedSystemParameter;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.PageView;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationModels.ParameterView;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterApplicationService;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import static org.mockito.ArgumentMatchers.any;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * System Parameter Web 与 Permission 引用安全测试。
 *
 * <p>测试启用真实 Spring Security FilterChain 和 Method Security，只用 jwt() 建立已验证测试身份；不关闭
 * 安全过滤器、不连接 Redis/JWK/数据库。业务服务使用 Mock 隔离 HTTP Adapter，PostgreSQL 行为由独立 IT 验证。</p>
 */
class SystemParameterWebSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private SystemParameterApplicationService service;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfiguration.class);
        context.refresh();
        service = context.getBean(SystemParameterApplicationService.class);
        reset(service);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void unauthenticatedRequestMustReturn401() throws Exception {
        mockMvc.perform(get("/api/system/admin/parameters/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestWithoutPermissionMustReturn403() throws Exception {
        mockMvc.perform(get("/api/system/admin/parameters/1").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void readPermissionMayQueryButMayNotWrite() throws Exception {
        when(service.get("1")).thenReturn(view());
        mockMvc.perform(get("/api/system/admin/parameters/1").with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.parameterValue").value("12"))
                .andExpect(jsonPath("$.class").doesNotExist());

        mockMvc.perform(post("/api/system/admin/parameters").with(readJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void writePermissionMayCreateUpdateAndChangeStatus() throws Exception {
        when(service.create(any())).thenReturn(view());
        when(service.update(anyString(), any())).thenReturn(view());
        when(service.changeStatus(anyString(), any())).thenReturn(view());

        mockMvc.perform(post("/api/system/admin/parameters").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/system/admin/parameters/1").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"valueType\":\"INTEGER\",\"parameterValue\":\"13\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/system/admin/parameters/1/status").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void readPermissionMayUseBoundedPageAndEffectiveResolution() throws Exception {
        when(service.page(any())).thenReturn(new PageView(List.of(view()), 1L, 0, 20));
        when(service.resolve("feature.timeout", "mom-web")).thenReturn(new ResolvedSystemParameter(
                "feature.timeout", ParameterValueType.INTEGER, "12", ParameterScopeType.GLOBAL, "", 0L,
                Instant.parse("2026-07-30T00:00:00Z")));
        mockMvc.perform(get("/api/system/admin/parameters").with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value("1"));
        mockMvc.perform(get("/api/system/parameters/feature.timeout")
                        .param("applicationCode", "mom-web").with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedScopeType").value("GLOBAL"))
                .andExpect(jsonPath("$.parameterValue").value("12"));
    }

    @Test
    void malformedRequestMustReturnStable400WithoutInternalDetails() throws Exception {
        mockMvc.perform(post("/api/system/admin/parameters").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopeType\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("请求参数格式非法"));
    }

    @Test
    void conflictAndNotFoundMustUseStableErrors() throws Exception {
        when(service.create(any())).thenThrow(new SystemParameterException.Conflict("参数已存在"));
        when(service.get("missing")).thenThrow(new SystemParameterException.NotFound("参数不存在"));
        mockMvc.perform(post("/api/system/admin/parameters").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
        mockMvc.perform(get("/api/system/admin/parameters/missing").with(readJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor readJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("system:parameter:read"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writeJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("system:parameter:write"));
    }

    private static String createBody() {
        return "{\"scopeType\":\"GLOBAL\",\"parameterKey\":\"feature.timeout\","
                + "\"valueType\":\"INTEGER\",\"parameterValue\":\"12\",\"enabled\":true}";
    }

    private static ParameterView view() {
        Instant now = Instant.parse("2026-07-30T00:00:00Z");
        return new ParameterView("1", ParameterScopeType.GLOBAL, "", "feature.timeout",
                ParameterValueType.INTEGER, "12", true, 0L, null,
                "actor-1", now, "actor-1", now);
    }

    /** 最小真实 MVC + Security 测试配置；Decoder 永不访问外部 JWK。 */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestWebConfiguration {
        @Bean
        SystemParameterApplicationService systemParameterApplicationService() {
            return mock(SystemParameterApplicationService.class);
        }

        @Bean
        SystemParameterController systemParameterController(SystemParameterApplicationService service) {
            return new SystemParameterController(service);
        }

        @Bean
        SystemParameterExceptionHandler systemParameterExceptionHandler() {
            return new SystemParameterExceptionHandler();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> { throw new IllegalStateException("Web 安全测试不得解码真实 Token"); };
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .oauth2ResourceServer(resource -> resource.jwt(withDefaults()));
            return http.build();
        }
    }
}
