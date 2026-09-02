package io.github.chrisshi.mom.security.autoconfigure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 默认 Opaque Token Resource Server 安全链测试。 */
@SpringBootTest(
    classes = MomServletResourceServerTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.main.banner-mode=off",
        "mom.security.resource-server.enabled=true",
        "mom.security.resource-server.public-paths=/public,/actuator/health/**,/error"
    }
)
class MomServletResourceServerTest {

    @Autowired
    WebApplicationContext applicationContext;

    @Autowired
    TestOpaqueTokenIntrospector introspector;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        introspector.checks = 0;
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    void protectedApiWithoutBearerTokenMustReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/protected"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validOpaqueTokenMustCreateAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-read-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("user-1"));

        org.assertj.core.api.Assertions.assertThat(introspector.checks).isEqualTo(1);
    }

    @Test
    void invalidOpaqueTokenMustReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserWithoutRequiredAuthorityMustReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-basic-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void publicPathMustNotRequireTokenIntrospection() throws Exception {
        mockMvc.perform(get("/public"))
            .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(introspector.checks).isZero();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ProtectedController.class)
    static class TestApplication {

        @Bean
        TestOpaqueTokenIntrospector opaqueTokenIntrospector() {
            return new TestOpaqueTokenIntrospector();
        }
    }

    @RestController
    static class ProtectedController {

        @GetMapping("/api/protected")
        @PreAuthorize("hasAuthority('mdm:material:read')")
        Map<String, String> protectedResource(Authentication authentication) {
            return Map.of("userId", authentication.getName());
        }

        @GetMapping("/public")
        Map<String, String> publicEndpoint() {
            return Map.of("status", "ok");
        }
    }

    static final class TestOpaqueTokenIntrospector implements OpaqueTokenIntrospector {
        private int checks;

        @Override
        public OAuth2AuthenticatedPrincipal introspect(String token) {
            checks++;

            if ("valid-read-token".equals(token)) {
                return principal(
                    "user-1",
                    List.of(new SimpleGrantedAuthority("mdm:material:read"))
                );
            }

            if ("valid-basic-token".equals(token)) {
                return principal(
                    "user-2",
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
            }

            throw new BadOpaqueTokenException("invalid access token");
        }

        private static OAuth2AuthenticatedPrincipal principal(
            String userId,
            Collection<GrantedAuthority> authorities
        ) {
            return new DefaultOAuth2AuthenticatedPrincipal(
                userId,
                Map.of("sub", userId),
                authorities
            );
        }
    }
}
