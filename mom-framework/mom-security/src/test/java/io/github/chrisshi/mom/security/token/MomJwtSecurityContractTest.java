package io.github.chrisshi.mom.security.token;

import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import io.github.chrisshi.mom.security.authorization.MomScopedResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** S06 JWT 协议、Authority、Permission、Factory/Party 与 404 防枚举契约测试。 */
class MomJwtSecurityContractTest {
    private static final String ISSUER = "https://iam.mom.example";
    private static final Instant NOW = Instant.now().minusSeconds(5);

    @Test
    void validInternalTokenMustMapRolesPermissionsAndFactoryScope() {
        Jwt jwt = internalJwt("mom-admin-web", List.of("mom-admin-web"));
        OAuth2TokenValidator<Jwt> validator = MomJwtValidators.create(
                ISSUER, MomSecurityClaims.publicClientIds());
        assertFalse(validator.validate(jwt).hasErrors());

        MomJwtGrantedAuthoritiesConverter converter = new MomJwtGrantedAuthoritiesConverter();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, converter.convert(jwt));
        MomAuthorizationService authorization = new MomAuthorizationService();

        assertEquals("user-1", authorization.current(authentication).userId());
        assertTrue(authorization.hasPermission(authentication, "mdm:material:read"));
        assertDoesNotThrow(() -> authorization.requireCurrentFactory(authentication, "factory-1"));
        assertThrows(AccessDeniedException.class,
                () -> authorization.requirePermission(authentication, "iam:user:write"));
        assertThrows(AccessDeniedException.class,
                () -> authorization.requireCurrentFactory(authentication, "factory-2"));
        assertDoesNotThrow(() -> authorization.requireObjectVisible(
                authentication, "factory-1", null, null));
        assertThrows(MomScopedResourceNotFoundException.class,
                () -> authorization.requireObjectVisible(authentication, "factory-2", null, null));
    }

    @Test
    void audienceAndClientIdMustBeConsistent() {
        OAuth2TokenValidator<Jwt> validator = MomJwtValidators.create(
                ISSUER, Set.of("mom-admin-web"));
        assertTrue(validator.validate(internalJwt(
                "mom-admin-web", List.of("mom-mobile-pda"))).hasErrors());
        assertTrue(validator.validate(internalJwt(
                "mom-supplier-web", List.of("mom-supplier-web"))).hasErrors());
    }

    @Test
    void externalTokenMustFixPartyAndHideForeignObjects() {
        Jwt jwt = baseJwt("mom-supplier-web", List.of("mom-supplier-web"))
                .claim(MomSecurityClaims.USER_TYPE, MomSecurityClaims.USER_TYPE_SUPPLIER)
                .claim(MomSecurityClaims.ROLES, List.of("SUPPLIER_OPERATOR"))
                .claim(MomSecurityClaims.PERMISSIONS, List.of("supplier:order:read"))
                .claim(MomSecurityClaims.FACTORY_IDS, List.of("factory-9"))
                .claim(MomSecurityClaims.PARTY_TYPE, MomSecurityClaims.USER_TYPE_SUPPLIER)
                .claim(MomSecurityClaims.PARTY_ID, "supplier-9")
                .build();
        assertFalse(MomJwtValidators.create(ISSUER, MomSecurityClaims.publicClientIds())
                .validate(jwt).hasErrors());

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt, new MomJwtGrantedAuthoritiesConverter().convert(jwt));
        MomAuthorizationService authorization = new MomAuthorizationService();
        assertDoesNotThrow(() -> authorization.requireObjectVisible(
                authentication, "factory-9", "SUPPLIER", "supplier-9"));
        assertThrows(MomScopedResourceNotFoundException.class,
                () -> authorization.requireObjectVisible(
                        authentication, "factory-9", "SUPPLIER", "supplier-other"));
        assertThrows(MomScopedResourceNotFoundException.class,
                () -> authorization.requireObjectVisible(
                        authentication, "factory-9", "CUSTOMER", "supplier-9"));
    }

    @Test
    void malformedClaimsMustBeRejected() {
        Jwt missingSid = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject("user-1")
                .audience(List.of("mom-admin-web"))
                .issuedAt(NOW)
                .notBefore(NOW)
                .expiresAt(NOW.plusSeconds(600))
                .claim("jti", "jti-1")
                .claim(MomSecurityClaims.CLIENT_ID, "mom-admin-web")
                .claim(MomSecurityClaims.USER_TYPE, "INTERNAL")
                .claim(MomSecurityClaims.ROLES, List.of())
                .claim(MomSecurityClaims.PERMISSIONS, List.of())
                .claim(MomSecurityClaims.FACTORY_IDS, List.of())
                .build();
        assertTrue(MomJwtValidators.create(ISSUER, MomSecurityClaims.publicClientIds())
                .validate(missingSid).hasErrors());
    }

    private static Jwt internalJwt(String clientId, List<String> audience) {
        return baseJwt(clientId, audience)
                .claim(MomSecurityClaims.USER_TYPE, MomSecurityClaims.USER_TYPE_INTERNAL)
                .claim(MomSecurityClaims.ROLES, List.of("PLATFORM_ADMIN"))
                .claim(MomSecurityClaims.PERMISSIONS, List.of("mdm:material:read"))
                .claim(MomSecurityClaims.FACTORY_IDS, List.of("factory-1"))
                .build();
    }

    private static Jwt.Builder baseJwt(String clientId, List<String> audience) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject("user-1")
                .audience(audience)
                .issuedAt(NOW)
                .notBefore(NOW)
                .expiresAt(NOW.plusSeconds(600))
                .claim("jti", "jti-1")
                .claim(MomSecurityClaims.SESSION_ID, "session-1")
                .claim(MomSecurityClaims.CLIENT_ID, clientId);
    }
}
