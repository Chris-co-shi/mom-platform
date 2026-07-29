package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PC JSON 与 Mobile OAuth2/OIDC Access Token 核心 Claims 的重构前特征测试。
 *
 * <p>测试精确比较安全 Claim allowlist；签发时间、过期时间、jti 等动态标准字段按各自语义断言，
 * 不使用大型快照，也不要求两个独立 Token 字符串相同。</p>
 */
@ExtendWith(MockitoExtension.class)
class IamDualChannelJwtClaimsCharacterizationTest {

    @Mock IamAuthorizationContextService contexts;
    @Mock IamSessionTokenService sessions;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 相同权威上下文和 Session 下，双通道核心授权 Claims 的名称、类型与值必须等价。 */
    @Test
    void dualChannelsMustKeepEquivalentCoreSecurityClaims() {
        Instant issuedAt = Instant.parse("2026-07-29T04:00:00Z");
        Instant expiresAt = issuedAt.plusSeconds(600);
        IamAuthorizationContext authorization = externalContext();
        when(contexts.loadByUsername("supplier")).thenReturn(authorization);
        when(sessions.issueInitial(
                "supplier", "mom-supplier-web", "127.0.0.1", "JUnit", "browser"))
                .thenReturn(new IamSessionTokenService.InitialIssue(
                        authorization, "session-1", "refresh-1", issuedAt, expiresAt,
                        issuedAt.plusSeconds(8 * 60 * 60)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        request.addHeader("X-Device-Name", "browser");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        JwtClaimsSet.Builder sasClaims = JwtClaimsSet.builder()
                .issuer("https://iam.example.test")
                .audience(List.of("mom-supplier-web"))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id("sas-jti");
        JwtEncodingContext sasContext = mock(JwtEncodingContext.class);
        RegisteredClient registeredClient = RegisteredClient.withId("client-row-1")
                .clientId("mom-supplier-web")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://supplier.example.test/callback")
                .build();
        when(sasContext.getPrincipal()).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated("supplier", "n/a", List.of()));
        when(sasContext.getRegisteredClient()).thenReturn(registeredClient);
        when(sasContext.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(sasContext.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        when(sasContext.getClaims()).thenReturn(sasClaims);

        OAuth2TokenCustomizer<JwtEncodingContext> customizer =
                new IamAuthorizationServerConfiguration().iamJwtCustomizer(contexts, sessions);
        customizer.customize(sasContext);
        Map<String, Object> mobileClaims = sasClaims.build().getClaims();

        CapturingJwtEncoder encoder = new CapturingJwtEncoder();
        IamAuthorizationProperties properties = new IamAuthorizationProperties();
        properties.getKey().setKeyId("kid-1");
        IamSessionJwtIssuer issuer = new IamSessionJwtIssuer(
                encoder,
                AuthorizationServerSettings.builder().issuer("https://iam.example.test").build(),
                properties);
        OAuth2AccessToken pcToken = issuer.issue(
                authorization, "session-1", "mom-supplier-web", issuedAt, expiresAt,
                Set.of("openid", "profile"));
        Map<String, Object> pcClaims = encoder.claims;

        assertThat(pcToken.getTokenValue()).isEqualTo("encoded-token");
        assertThat(selectCoreClaims(mobileClaims)).isEqualTo(selectCoreClaims(pcClaims));
        assertThat(pcClaims.get("iss")).hasToString("https://iam.example.test");
        assertThat(pcClaims.get("aud")).isEqualTo(List.of("mom-supplier-web"));
        assertThat(pcClaims.get("iat")).isEqualTo(issuedAt);
        assertThat(pcClaims.get("exp")).isEqualTo(expiresAt);
        assertThat(pcClaims.get("jti")).isInstanceOf(String.class);
        assertThat(request.getAttribute(IamSessionTokenService.REQUEST_REFRESH_TOKEN_ATTRIBUTE))
                .isEqualTo("refresh-1");
        assertThat(request.getAttribute(IamSessionTokenService.REQUEST_SESSION_ID_ATTRIBUTE))
                .isEqualTo("session-1");
    }

    /** 核心 Claims 必须逐项列入 allowlist，协议专属或动态字段不得被宽泛忽略。 */
    private static Map<String, Object> selectCoreClaims(Map<String, Object> claims) {
        Map<String, Object> selected = new LinkedHashMap<>();
        for (String name : List.of(
                "sub", "sid", "client_id", "user_type", "roles", "permissions",
                "factory_ids", "party_type", "party_id")) {
            selected.put(name, claims.get(name));
        }
        return selected;
    }

    private static IamAuthorizationContext externalContext() {
        return new IamAuthorizationContext(
                "user-1", "supplier", "Supplier User", UserType.SUPPLIER,
                List.of("SUPPLIER_OPERATOR"), List.of("order:read"), List.of("factory-1"),
                PartyType.SUPPLIER, "party-1");
    }

    /** 捕获待签名 Header/Claims，避免特征测试生成或持有真实私钥。 */
    private static final class CapturingJwtEncoder implements JwtEncoder {
        private Map<String, Object> claims;

        @Override
        public Jwt encode(JwtEncoderParameters parameters) {
            claims = new LinkedHashMap<>(parameters.getClaims().getClaims());
            return Jwt.withTokenValue("encoded-token")
                    .headers(headers -> headers.putAll(parameters.getJwsHeader().getHeaders()))
                    .claims(values -> values.putAll(claims))
                    .build();
        }
    }
}
