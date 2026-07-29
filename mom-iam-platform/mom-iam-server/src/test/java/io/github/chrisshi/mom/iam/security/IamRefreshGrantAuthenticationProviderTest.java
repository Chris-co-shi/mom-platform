package io.github.chrisshi.mom.iam.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标准 OAuth2 Refresh Grant Adapter 的特征测试。
 *
 * <p>测试锁定 Client Principal 分类、Grant 允许列表、唯一 Rotation/Access Token
 * 核心调用顺序和 SAS Token 映射，不启动 HTTP、数据库或 Redis。</p>
 */
@ExtendWith(MockitoExtension.class)
class IamRefreshGrantAuthenticationProviderTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-07-29T05:00:00Z");

    @Mock IamSessionTokenService sessions;
    @Mock IamAccessTokenIssuer tokenIssuer;

    private IamRefreshGrantAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new IamRefreshGrantAuthenticationProvider(sessions, tokenIssuer);
    }

    /** 未认证 Client 必须保持 invalid_client，且不得消费 Refresh。 */
    @Test
    void unauthenticatedPrincipalMustRemainInvalidClient() {
        IamRefreshGrantAuthenticationToken request = new IamRefreshGrantAuthenticationToken(
                "refresh-1", new TestingAuthenticationToken("client", "n/a"), Map.of());

        assertThatThrownBy(() -> provider.authenticate(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(error -> ((OAuth2AuthenticationException) error).getError().getErrorCode())
                .isEqualTo("invalid_client");
        verify(sessions, never()).rotate("refresh-1", "mom-mobile-pda");
    }

    /** Client 未登记 refresh_token Grant 时必须保持 unauthorized_client。 */
    @Test
    void clientWithoutRefreshGrantMustRemainUnauthorizedClient() {
        RegisteredClient client = client(false);
        OAuth2ClientAuthenticationToken principal = principal(client);
        IamRefreshGrantAuthenticationToken request = new IamRefreshGrantAuthenticationToken(
                "refresh-1", principal, Map.of());

        assertThatThrownBy(() -> provider.authenticate(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(error -> ((OAuth2AuthenticationException) error).getError().getErrorCode())
                .isEqualTo("unauthorized_client");
        verify(sessions, never()).rotate("refresh-1", client.getClientId());
    }

    /** 标准 Adapter 必须先 Rotation，再用共享 Issuer 签发并映射 snake_case 响应所需 Token。 */
    @Test
    void successfulRefreshMustDelegateToSharedCoreAndMapSasTokens() {
        RegisteredClient client = client(true);
        OAuth2ClientAuthenticationToken principal = principal(client);
        IamRefreshGrantAuthenticationToken request = new IamRefreshGrantAuthenticationToken(
                "refresh-1", principal, Map.of());
        IamAuthorizationContext context = new IamAuthorizationContext(
                "user-1", "admin", "Administrator",
                io.github.chrisshi.mom.iam.domain.type.UserType.INTERNAL,
                List.of("MOM_ADMIN"), List.of("iam:user:read"), List.of("factory-1"),
                null, null);
        IamSessionTokenService.Rotation rotation = new IamSessionTokenService.Rotation(
                context, "session-1", "refresh-2", ISSUED_AT,
                ISSUED_AT.plusSeconds(600), ISSUED_AT.plusSeconds(3600), 2L);
        when(sessions.rotate("refresh-1", client.getClientId())).thenReturn(rotation);
        when(tokenIssuer.issue(
                context, "session-1", client.getClientId(), ISSUED_AT,
                ISSUED_AT.plusSeconds(600), client.getScopes()))
                .thenReturn(new IamAccessTokenIssuer.IssuedAccessToken(
                        "access-2", ISSUED_AT, ISSUED_AT.plusSeconds(600), client.getScopes()));

        OAuth2AccessTokenAuthenticationToken result =
                (OAuth2AccessTokenAuthenticationToken) provider.authenticate(request);

        InOrder order = inOrder(sessions, tokenIssuer);
        order.verify(sessions).rotate("refresh-1", client.getClientId());
        order.verify(tokenIssuer).issue(
                context, "session-1", client.getClientId(), ISSUED_AT,
                ISSUED_AT.plusSeconds(600), client.getScopes());
        assertThat(result.getAccessToken().getTokenValue()).isEqualTo("access-2");
        assertThat(result.getAccessToken().getTokenType().getValue()).isEqualTo("Bearer");
        assertThat(result.getAccessToken().getScopes()).containsExactlyInAnyOrder("openid", "profile");
        assertThat(result.getRefreshToken()).isNotNull();
        assertThat(result.getRefreshToken().getTokenValue()).isEqualTo("refresh-2");
        assertThat(result.getRefreshToken().getExpiresAt()).isEqualTo(ISSUED_AT.plusSeconds(3600));
    }

    /** Rotation 的标准 invalid_grant 必须原样传播，不得转成 MOM JSON 错误。 */
    @Test
    void rotationFailureMustPreserveInvalidGrant() {
        RegisteredClient client = client(true);
        when(sessions.rotate("refresh-expired", client.getClientId()))
                .thenThrow(new OAuth2AuthenticationException("invalid_grant"));

        IamRefreshGrantAuthenticationToken request = new IamRefreshGrantAuthenticationToken(
                "refresh-expired", principal(client), Map.of());

        assertThatThrownBy(() -> provider.authenticate(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(error -> ((OAuth2AuthenticationException) error).getError().getErrorCode())
                .isEqualTo("invalid_grant");
        verify(tokenIssuer, never()).issue(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static RegisteredClient client(boolean allowRefresh) {
        RegisteredClient.Builder builder = RegisteredClient.withId("client-row-1")
                .clientId("mom-mobile-pda")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("com.mom.mobile:/oauth2/callback")
                .scope("openid")
                .scope("profile");
        if (allowRefresh) builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        return builder.build();
    }

    private static OAuth2ClientAuthenticationToken principal(RegisteredClient client) {
        return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
    }
}
