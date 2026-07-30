package io.github.chrisshi.mom.system.web.dictionary;

import io.github.chrisshi.mom.system.api.ResolvedSystemDictionaryItem;
import io.github.chrisshi.mom.system.api.SystemDictionaryItemOption;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.DictionaryPageView;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.DictionaryView;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.ItemPageView;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.ItemView;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationService;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * System Dictionary Web 与 Permission Reference 安全测试。
 *
 * <p>测试启用真实 SecurityFilterChain 和 Method Security，只用 jwt() 建立测试身份；不关闭过滤器、
 * 不连接 Redis/JWK/数据库。Mock 仅隔离 HTTP Adapter，真实 PostgreSQL 行为由独立 IT 验证。</p>
 */
class SystemDictionaryWebSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private SystemDictionaryApplicationService service;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfiguration.class);
        context.refresh();
        service = context.getBean(SystemDictionaryApplicationService.class);
        reset(service);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void unauthenticatedDictionaryRequestMustReturn401() throws Exception {
        mockMvc.perform(get("/api/system/admin/dictionaries/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestWithoutPermissionMustReturn403() throws Exception {
        mockMvc.perform(get("/api/system/dictionaries/system.common.state/items").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void readPermissionMayQueryAdminActiveAndCompatibilityWithoutEntityLeak() throws Exception {
        when(service.getDictionary("1")).thenReturn(dictionaryView());
        when(service.pageDictionaries(any())).thenReturn(
                new DictionaryPageView(List.of(dictionaryView()), 1L, 0, 20));
        when(service.pageItems(anyString(), any())).thenReturn(
                new ItemPageView(List.of(itemView()), 1L, 0, 20));
        when(service.activeItems("system.common.state")).thenReturn(List.of(option()));
        when(service.resolveItem("system.common.state", "ready")).thenReturn(resolved(false, true));

        mockMvc.perform(get("/api/system/admin/dictionaries/1").with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.class").doesNotExist());
        mockMvc.perform(get("/api/system/admin/dictionaries/1/items").with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].itemCode").value("ready"));
        mockMvc.perform(get("/api/system/dictionaries/system.common.state/items").with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dictionaryId").doesNotExist())
                .andExpect(jsonPath("$[0].itemCode").value("ready"));
        mockMvc.perform(get("/api/system/dictionaries/system.common.state/items/ready").with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dictionaryEnabled").value(false))
                .andExpect(jsonPath("$.itemEnabled").value(true))
                .andExpect(jsonPath("$.effectiveEnabled").value(false));
    }

    @Test
    void readPermissionMustNotWrite() throws Exception {
        mockMvc.perform(post("/api/system/admin/dictionaries").with(readJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(createDictionaryBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/system/admin/dictionaries/1/items/2/status").with(readJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"version\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void writePermissionMayCreateUpdateAndChangeBothStatuses() throws Exception {
        when(service.createDictionary(any())).thenReturn(dictionaryView());
        when(service.updateDictionary(anyString(), any())).thenReturn(dictionaryView());
        when(service.changeDictionaryStatus(anyString(), any())).thenReturn(dictionaryView());
        when(service.createItem(anyString(), any())).thenReturn(itemView());
        when(service.updateItem(anyString(), anyString(), any())).thenReturn(itemView());
        when(service.changeItemStatus(anyString(), anyString(), any())).thenReturn(itemView());

        mockMvc.perform(post("/api/system/admin/dictionaries").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(createDictionaryBody()))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/system/admin/dictionaries/1").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictionaryName\":\"State\",\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/system/admin/dictionaries/1/status").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/system/admin/dictionaries/1/items").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(createItemBody()))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/system/admin/dictionaries/1/items/2").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemLabel\":\"Ready\",\"sortOrder\":10,\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/system/admin/dictionaries/1/items/2/status").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void malformedRequestMustReturnStable400() throws Exception {
        mockMvc.perform(post("/api/system/admin/dictionaries").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("请求参数格式非法"));
    }

    @Test
    void conflictAndNotFoundMustUseStableErrors() throws Exception {
        when(service.createDictionary(any())).thenThrow(new SystemDictionaryException.Conflict("字典已存在"));
        when(service.getDictionary("missing")).thenThrow(new SystemDictionaryException.NotFound("字典不存在"));
        mockMvc.perform(post("/api/system/admin/dictionaries").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(createDictionaryBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
        mockMvc.perform(get("/api/system/admin/dictionaries/missing").with(readJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor readJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("system:dictionary:read"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writeJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("system:dictionary:write"));
    }

    private static String createDictionaryBody() {
        return "{\"dictionaryCode\":\"system.common.state\",\"dictionaryName\":\"State\",\"enabled\":true}";
    }

    private static String createItemBody() {
        return "{\"itemCode\":\"ready\",\"itemLabel\":\"Ready\",\"sortOrder\":10,\"enabled\":true}";
    }

    private static DictionaryView dictionaryView() {
        Instant now = Instant.parse("2026-07-30T00:00:00Z");
        return new DictionaryView("1", "system.common.state", "State", true, 0L, null,
                "actor-1", now, "actor-1", now);
    }

    private static ItemView itemView() {
        Instant now = Instant.parse("2026-07-30T00:00:00Z");
        return new ItemView("2", "1", "ready", "Ready", 10, true, 0L, null,
                "actor-1", now, "actor-1", now);
    }

    private static SystemDictionaryItemOption option() {
        return new SystemDictionaryItemOption("system.common.state", "ready", "Ready", 10, 0L,
                Instant.parse("2026-07-30T00:00:00Z"));
    }

    private static ResolvedSystemDictionaryItem resolved(boolean dictionaryEnabled, boolean itemEnabled) {
        return new ResolvedSystemDictionaryItem("system.common.state", "ready", "Ready", dictionaryEnabled,
                itemEnabled, dictionaryEnabled && itemEnabled, 0L, Instant.parse("2026-07-30T00:00:00Z"));
    }

    /** 最小真实 MVC + Security 配置；Decoder 永不访问外部 JWK。 */
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestWebConfiguration {
        @Bean
        SystemDictionaryApplicationService systemDictionaryApplicationService() {
            return mock(SystemDictionaryApplicationService.class);
        }

        @Bean
        SystemDictionaryController systemDictionaryController(SystemDictionaryApplicationService service) {
            return new SystemDictionaryController(service);
        }

        @Bean
        SystemDictionaryExceptionHandler systemDictionaryExceptionHandler() {
            return new SystemDictionaryExceptionHandler();
        }

        @Bean
        JwtDecoder dictionaryJwtDecoder() {
            return token -> {
                throw new IllegalStateException("Web 安全测试不得解码真实 Token");
            };
        }

        @Bean
        SecurityFilterChain dictionarySecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .oauth2ResourceServer(resource -> resource.jwt(withDefaults()));
            return http.build();
        }
    }
}
