package io.github.chrisshi.mom.security.autoconfigure;

import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import io.github.chrisshi.mom.security.authorization.MomScopedResourceNotFoundException;
import io.github.chrisshi.mom.security.revocation.MomRevocationStoreUnavailableException;
import io.github.chrisshi.mom.security.revocation.MomRevokedSessionChecker;
import io.github.chrisshi.mom.security.token.MomSecurityClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S06 业务服务二次验证、Permission、Factory/Party 与 404 防枚举集成测试。 */
@SpringBootTest(
        classes = MomServletResourceServerTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "mom.security.resource-server.enabled=true",
                "mom.security.resource-server.issuer-uri=https://iam.mom.example",
                "mom.security.resource-server.jwk-set-uri=https://iam.mom.example/oauth2/jwks",
                "mom.security.resource-server.public-paths=/public,/actuator/health/**,/error"
        })
class MomServletResourceServerTest {
    @Autowired WebApplicationContext applicationContext;
    @Autowired TestRevokedSessionChecker revokedSessions;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        revokedSessions.mode = RevocationMode.ACTIVE;
        revokedSessions.checks = 0;
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void protectedApiWithoutBearerMustReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeSidMustAllowDirectBusinessServiceRequest() throws Exception {
        mockMvc.perform(get("/api/protected")
                        .header("X-Factory-Id", "factory-1")
                        .with(internalJwt("mdm:material:read")))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(revokedSessions.checks).isEqualTo(1);
    }

    @Test
    void revokedOrMissingSidMustReturnUnauthorized() throws Exception {
        revokedSessions.mode = RevocationMode.REVOKED;
        mockMvc.perform(get("/api/protected")
                        .header("X-Factory-Id", "factory-1")
                        .with(internalJwt("mdm:material:read")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")));

        mockMvc.perform(get("/api/protected")
                        .header("X-Factory-Id", "factory-1")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("user-1")
                                .audience(List.of("mom-admin-web"))
                                .claim(MomSecurityClaims.CLIENT_ID, "mom-admin-web"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")));
    }

    @Test
    void revocationStoreFailureMustFailClosedWithServiceUnavailable() throws Exception {
        revokedSessions.mode = RevocationMode.UNAVAILABLE;

        mockMvc.perform(get("/api/protected")
                        .header("X-Factory-Id", "factory-1")
                        .with(internalJwt("mdm:material:read")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error").value("revocation_store_unavailable"));
    }

    @Test
    void publicPathMustNotDependOnRevocationStore() throws Exception {
        revokedSessions.mode = RevocationMode.UNAVAILABLE;

        mockMvc.perform(get("/public"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(revokedSessions.checks).isZero();
    }

    @Test
    void forgedIdentityHeaderMustNotReplaceJwtSubjectOnDirectRequest() throws Exception {
        mockMvc.perform(get("/api/protected")
                        .header("X-Factory-Id", "factory-1")
                        .header("X-MOM-USER-ID", "attacker")
                        .with(internalJwt("mdm:material:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    @Test
    void permissionAndCurrentFactoryMustBeEnforcedByBusinessService() throws Exception {
        mockMvc.perform(get("/api/protected")
                        .header("X-Factory-Id", "factory-1")
                        .with(internalJwt("mdm:material:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.factoryId").value("factory-1"));

        mockMvc.perform(get("/api/protected")
                        .header("X-Factory-Id", "factory-1")
                        .with(internalJwt("mdm:material:list")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/protected")
                        .header("X-Factory-Id", "factory-other")
                        .with(internalJwt("mdm:material:read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void foreignFactoryOrPartyMustReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/object")
                        .param("factoryId", "factory-other")
                        .with(supplierJwt()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/object")
                        .param("factoryId", "factory-9")
                        .param("partyType", "SUPPLIER")
                        .param("partyId", "supplier-other")
                        .with(supplierJwt()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/object")
                        .param("factoryId", "factory-9")
                        .param("partyType", "SUPPLIER")
                        .param("partyId", "supplier-9")
                        .with(supplierJwt()))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor internalJwt(
            String permission) {
        return jwt().jwt(jwt -> jwt
                        .subject("user-1")
                        .audience(List.of("mom-admin-web"))
                        .claim(MomSecurityClaims.SESSION_ID, "session-1")
                        .claim(MomSecurityClaims.CLIENT_ID, "mom-admin-web")
                        .claim(MomSecurityClaims.USER_TYPE, MomSecurityClaims.USER_TYPE_INTERNAL)
                        .claim(MomSecurityClaims.ROLES, List.of("PLATFORM_ADMIN"))
                        .claim(MomSecurityClaims.PERMISSIONS, List.of(permission))
                        .claim(MomSecurityClaims.FACTORY_IDS, List.of("factory-1")))
                .authorities(new SimpleGrantedAuthority(permission));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor supplierJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("supplier-user-1")
                        .audience(List.of("mom-supplier-web"))
                        .claim(MomSecurityClaims.SESSION_ID, "session-9")
                        .claim(MomSecurityClaims.CLIENT_ID, "mom-supplier-web")
                        .claim(MomSecurityClaims.USER_TYPE, MomSecurityClaims.USER_TYPE_SUPPLIER)
                        .claim(MomSecurityClaims.ROLES, List.of("SUPPLIER_OPERATOR"))
                        .claim(MomSecurityClaims.PERMISSIONS, List.of("supplier:order:read"))
                        .claim(MomSecurityClaims.FACTORY_IDS, List.of("factory-9"))
                        .claim(MomSecurityClaims.PARTY_TYPE, "SUPPLIER")
                        .claim(MomSecurityClaims.PARTY_ID, "supplier-9"))
                .authorities(new SimpleGrantedAuthority("supplier:order:read"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ProtectedController.class, ScopedNotFoundAdvice.class})
    static class TestApplication {
        /** jwt() 直接建立 SecurityContext；该 Bean 只满足 Resource Server 启动条件，不访问外部 JWK。 */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new IllegalStateException("测试不应调用真实 JwtDecoder");
            };
        }

        @Bean
        TestRevokedSessionChecker revokedSessionChecker() {
            return new TestRevokedSessionChecker();
        }
    }

    @RestController
    static class ProtectedController {
        private final MomAuthorizationService authorization;

        ProtectedController(MomAuthorizationService authorization) {
            this.authorization = authorization;
        }

        @GetMapping("/api/protected")
        Map<String, String> protectedResource(
                Authentication authentication,
                @RequestHeader("X-Factory-Id") String factoryId) {
            authorization.requirePermission(authentication, "mdm:material:read");
            authorization.requireCurrentFactory(authentication, factoryId);
            return Map.of(
                    "userId", authorization.current(authentication).userId(),
                    "factoryId", factoryId);
        }

        @GetMapping("/api/object")
        Map<String, String> scopedObject(
                Authentication authentication,
                @RequestParam String factoryId,
                @RequestParam(required = false) String partyType,
                @RequestParam(required = false) String partyId) {
            authorization.requireObjectVisible(authentication, factoryId, partyType, partyId);
            return Map.of("status", "visible");
        }

        @GetMapping("/public")
        Map<String, String> publicEndpoint() {
            return Map.of("status", "ok");
        }
    }

    @RestControllerAdvice
    static class ScopedNotFoundAdvice {
        @ExceptionHandler(MomScopedResourceNotFoundException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        Map<String, String> notFound() {
            return Map.of("error", "not_found");
        }
    }

    enum RevocationMode { ACTIVE, REVOKED, UNAVAILABLE }

    /** 可控的协议中立撤销检查器，仅用于验证 Resource Server 失败语义。 */
    static final class TestRevokedSessionChecker implements MomRevokedSessionChecker {
        private RevocationMode mode = RevocationMode.ACTIVE;
        private int checks;

        @Override
        public boolean isRevoked(String sessionId) {
            checks++;
            if (mode == RevocationMode.UNAVAILABLE) {
                throw new MomRevocationStoreUnavailableException("测试撤销存储不可用");
            }
            return mode == RevocationMode.REVOKED;
        }
    }
}
