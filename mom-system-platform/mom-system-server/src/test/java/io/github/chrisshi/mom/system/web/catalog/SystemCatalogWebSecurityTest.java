package io.github.chrisshi.mom.system.web.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ApplicationType;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.I18nReference;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeApplicationCatalog;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeCatalogView;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeChannelCatalog;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeNavigationItem;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.ApplicationView;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.CatalogReleaseView;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.RuntimeResult;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationService;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogNavigationMoveApplicationService;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogPublishOrchestrator;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogRuntimeApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Catalog Runtime 与 read/write/publish Permission 分离的真实 MVC SecurityFilterChain 测试。 */
class SystemCatalogWebSecurityTest {
    private static final String CHECKSUM = "a".repeat(64);
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private SystemCatalogApplicationService service;
    private SystemCatalogRuntimeApplicationService runtimeService;
    private SystemCatalogPublishOrchestrator publishOrchestrator;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfiguration.class);
        context.refresh();
        service = context.getBean(SystemCatalogApplicationService.class);
        runtimeService = context.getBean(SystemCatalogRuntimeApplicationService.class);
        publishOrchestrator = context.getBean(SystemCatalogPublishOrchestrator.class);
        reset(service, runtimeService, publishOrchestrator);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void runtimeMustRequireAuthenticationFilterAuthoritiesAndExposeNoExecutableFields()
            throws Exception {
        mockMvc.perform(get("/api/system/catalog/me")).andExpect(status().isUnauthorized());
        when(runtimeService.runtimeCatalog(anySet())).thenReturn(runtime());
        mockMvc.perform(get("/api/system/catalog/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("iam:user:read"))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + CHECKSUM + "\""))
                .andExpect(jsonPath("$.applications[0].applicationCode").value("iam"))
                .andExpect(jsonPath("$.applications[0].channels[0].navigation[0].routeKey")
                        .value("iam.users"))
                .andExpect(jsonPath("$.applications[0].id").doesNotExist())
                .andExpect(jsonPath("$.applications[0].channels[0].navigation[0].component")
                        .doesNotExist())
                .andExpect(jsonPath("$.applications[0].channels[0].navigation[0].path")
                        .doesNotExist());
    }

    @Test
    void matchingEtagMustReturn304WithoutBody() throws Exception {
        when(runtimeService.runtimeCatalog(anySet())).thenReturn(runtime());
        mockMvc.perform(get("/api/system/catalog/me")
                        .header("If-None-Match", "\"" + CHECKSUM + "\"").with(jwt()))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", "\"" + CHECKSUM + "\""))
                .andExpect(content().string(""));
    }

    @Test
    void adminMustSeparateReadWritePublishAndRejectUnknownFields() throws Exception {
        when(service.getApplication("1")).thenReturn(application());
        when(service.createApplication(any())).thenReturn(application());
        when(publishOrchestrator.publish(anyString(), any())).thenReturn(release());

        mockMvc.perform(get("/api/system/admin/applications/1").with(jwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/system/admin/applications/1").with(readJwt()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/system/admin/applications").with(readJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/system/admin/applications").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/system/admin/applications/1/catalog/publish").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationVersion\":0,\"changeNote\":\"initial\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/system/admin/applications/1/catalog/publish").with(publishJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicationVersion\":0,\"changeNote\":\"initial\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/system/admin/applications").with(writeJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody().replace("}", ",\"component\":\"../App.vue\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor readJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("system:catalog:read"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writeJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("system:catalog:write"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor publishJwt() {
        return jwt().authorities(new SimpleGrantedAuthority("system:catalog:publish"));
    }

    private static String createBody() {
        return "{\"applicationCode\":\"iam\",\"applicationType\":\"PLATFORM\","
                + "\"i18nResourceCode\":\"mom-web\",\"i18nMessageKey\":\"mom.menu.iam\","
                + "\"routeContractVersion\":1,\"sortOrder\":10,\"enabled\":true}";
    }

    private static ApplicationView application() {
        return new ApplicationView("1", "iam", ApplicationType.PLATFORM, "mom-web", "mom.menu.iam",
                null, null, 1, 10, true, 0, 0, "actor", Instant.EPOCH, "actor", Instant.EPOCH);
    }

    private static CatalogReleaseView release() {
        return new CatalogReleaseView("iam", 1, 1, 1, CHECKSUM, 1,
                null, 1, Instant.EPOCH);
    }

    private static RuntimeResult runtime() {
        var node = new RuntimeNavigationItem("iam.users", NavigationType.ROUTE, "iam:user:read",
                new I18nReference("mom-web", "mom.menu.users"), null,
                true, true, true, false, List.of());
        var app = new RuntimeApplicationCatalog("iam", ApplicationType.PLATFORM, 1, 1,
                new I18nReference("mom-web", "mom.menu.iam"), null,
                List.of(new RuntimeChannelCatalog(ClientChannel.WEB, List.of(node))));
        return new RuntimeResult(new RuntimeCatalogView(1, Instant.EPOCH, List.of(app)), CHECKSUM);
    }

    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestWebConfiguration {
        @Bean
        @Primary
        SystemCatalogApplicationService catalogApplicationService() {
            return mock(SystemCatalogApplicationService.class);
        }

        @Bean
        SystemCatalogRuntimeApplicationService catalogRuntimeApplicationService() {
            return mock(SystemCatalogRuntimeApplicationService.class);
        }

        @Bean
        SystemCatalogNavigationMoveApplicationService catalogMoveService() {
            return mock(SystemCatalogNavigationMoveApplicationService.class);
        }

        @Bean
        SystemCatalogPublishOrchestrator catalogPublishOrchestrator() {
            return mock(SystemCatalogPublishOrchestrator.class);
        }

        @Bean
        SystemCatalogAdminController catalogAdminController(
                SystemCatalogApplicationService service,
                SystemCatalogNavigationMoveApplicationService moveService,
                SystemCatalogPublishOrchestrator publishOrchestrator) {
            return new SystemCatalogAdminController(service, moveService, publishOrchestrator);
        }

        @Bean
        SystemCatalogRuntimeController catalogRuntimeController(
                SystemCatalogRuntimeApplicationService service) {
            return new SystemCatalogRuntimeController(service);
        }

        @Bean
        SystemCatalogExceptionHandler catalogExceptionHandler() {
            return new SystemCatalogExceptionHandler();
        }

        @Bean
        JwtDecoder catalogJwtDecoder() {
            return token -> { throw new IllegalStateException("No real JWT"); };
        }

        @Bean
        SecurityFilterChain catalogSecurity(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .oauth2ResourceServer(resource -> resource.jwt(withDefaults()));
            return http.build();
        }
    }
}
