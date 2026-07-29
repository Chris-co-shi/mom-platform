package io.github.chrisshi.mom.iam.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Authorization Server Public Client 与 Token Endpoint 的协议特征测试。
 *
 * <p>该测试冻结 Authorization Code + PKCE S256、标准 Refresh、RS256 ID Token 和标准响应字段，
 * 并显式证明不存在 Password Grant；它不把标准协议响应映射为 MOM 第一方 JSON 错误模型。</p>
 */
class IamOAuth2ProtocolCharacterizationTest {

    /** 四个 Public Client 只能登记 Authorization Code 与 Refresh，不得出现 Password Grant。 */
    @Test
    void publicClientsMustRemainPkceAuthorizationCodeClientsWithoutPasswordGrant() throws Exception {
        CapturingRegisteredClientRepository repository = new CapturingRegisteredClientRepository();
        IamAuthorizationProperties properties = properties();

        new IamPublicClientRegistrar(repository, properties)
                .run(new DefaultApplicationArguments(new String[0]));

        assertThat(repository.saved).hasSize(4);
        assertThat(repository.saved).allSatisfy(client -> {
            assertThat(client.getClientAuthenticationMethods())
                    .containsExactly(ClientAuthenticationMethod.NONE);
            assertThat(client.getAuthorizationGrantTypes())
                    .containsExactlyInAnyOrder(
                            AuthorizationGrantType.AUTHORIZATION_CODE,
                            AuthorizationGrantType.REFRESH_TOKEN);
            assertThat(client.getAuthorizationGrantTypes())
                    .noneMatch(type -> "password".equals(type.getValue()));
            assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
            assertThat(client.getTokenSettings().isReuseRefreshTokens()).isFalse();
            assertThat(client.getTokenSettings().getAccessTokenTimeToLive())
                    .isEqualTo(java.time.Duration.ofMinutes(10));
            assertThat(client.getTokenSettings().getIdTokenSignatureAlgorithm())
                    .isEqualTo(SignatureAlgorithm.RS256);
        });
    }

    /** 标准 Token Endpoint 成功响应必须保留 snake_case OAuth2 字段和 no-store。 */
    @Test
    void tokenEndpointResponseMustRemainStandardOAuth2Json() throws Exception {
        Instant issuedAt = Instant.now();
        RegisteredClient client = RegisteredClient.withId("client-row-1")
                .clientId("mom-mobile-pda")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("com.mom.mobile:/oauth2/callback")
                .build();
        OAuth2ClientAuthenticationToken principal = new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.NONE, null);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "access-1", issuedAt,
                issuedAt.plusSeconds(600), Set.of("openid", "profile"));
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                "refresh-1", issuedAt, issuedAt.plusSeconds(3600));
        OAuth2AccessTokenAuthenticationToken authentication =
                new OAuth2AccessTokenAuthenticationToken(
                        client, principal, accessToken, refreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new IamTokenResponseHandler().onAuthenticationSuccess(
                new MockHttpServletRequest(), response, authentication);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getContentAsString())
                .contains("\"access_token\":\"access-1\"")
                .contains("\"token_type\":\"Bearer\"")
                .contains("\"refresh_token\":\"refresh-1\"")
                .contains("\"scope\":\"")
                .contains("openid", "profile")
                .doesNotContain("accessToken", "refreshToken", "sessionId");
    }

    /** 标准字段不得被附加参数覆盖，未知对象类型必须失败关闭。 */
    @Test
    void tokenEndpointAdditionalParametersMustNotOverrideStandards() throws Exception {
        Instant issuedAt = Instant.now();
        RegisteredClient client = RegisteredClient.withId("client-row-1")
                .clientId("mom-mobile-pda")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .build();
        OAuth2ClientAuthenticationToken principal = new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.NONE, null);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "access-1", issuedAt,
                issuedAt.plusSeconds(600), Set.of("openid"));
        OAuth2AccessTokenAuthenticationToken authentication =
                new OAuth2AccessTokenAuthenticationToken(
                        client, principal, accessToken, null,
                        Map.of("access_token", "must-not-win", "custom_number", 7));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new IamTokenResponseHandler().onAuthenticationSuccess(
                new MockHttpServletRequest(), response, authentication);

        assertThat(response.getContentAsString())
                .contains("\"access_token\":\"access-1\"")
                .contains("\"custom_number\":7")
                .doesNotContain("must-not-win");
    }

    private static IamAuthorizationProperties properties() {
        IamAuthorizationProperties properties = new IamAuthorizationProperties();
        properties.setIssuer(URI.create("https://iam.example.test"));
        properties.getKey().setKeyId("kid-1");
        properties.getKey().setPrivateKeyLocation(new ClassPathResource("test-iam-private.pem"));
        properties.getKey().setPublicKeyLocation(new ClassPathResource("test-iam-public.pem"));
        configure(properties.getAdminWeb(), "https://admin.example.test/callback");
        configure(properties.getSupplierWeb(), "https://supplier.example.test/callback");
        configure(properties.getCustomerWeb(), "https://customer.example.test/callback");
        configure(properties.getMobilePda(), "com.mom.mobile:/oauth2/callback");
        return properties;
    }

    private static void configure(IamAuthorizationProperties.Client client, String redirectUri) {
        client.setRedirectUri(URI.create(redirectUri));
        client.setPostLogoutRedirectUri(URI.create(redirectUri + "/logout"));
    }

    /** 只捕获注册结果，不连接 SAS JDBC Store。 */
    private static final class CapturingRegisteredClientRepository
            implements RegisteredClientRepository {
        private final List<RegisteredClient> saved = new ArrayList<>();

        @Override
        public void save(RegisteredClient registeredClient) {
            saved.add(registeredClient);
        }

        @Override
        public RegisteredClient findById(String id) {
            return null;
        }

        @Override
        public RegisteredClient findByClientId(String clientId) {
            return null;
        }
    }
}
