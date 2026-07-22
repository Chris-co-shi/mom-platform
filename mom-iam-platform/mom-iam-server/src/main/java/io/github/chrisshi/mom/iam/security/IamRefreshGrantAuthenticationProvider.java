package io.github.chrisshi.mom.iam.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/** 使用 MOM Session/Refresh 权威状态处理标准 refresh_token Grant。 */
public final class IamRefreshGrantAuthenticationProvider implements AuthenticationProvider {
    private final IamSessionTokenService sessions;
    private final IamSessionJwtIssuer jwtIssuer;

    public IamRefreshGrantAuthenticationProvider(
            IamSessionTokenService sessions,
            IamSessionJwtIssuer jwtIssuer) {
        this.sessions = sessions;
        this.jwtIssuer = jwtIssuer;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        IamRefreshGrantAuthenticationToken refreshGrant =
                (IamRefreshGrantAuthenticationToken) authentication;
        OAuth2ClientAuthenticationToken clientPrincipal = authenticatedClient(refreshGrant);
        RegisteredClient client = clientPrincipal.getRegisteredClient();
        if (client == null
                || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }

        IamSessionTokenService.Rotation rotation = sessions.rotate(
                refreshGrant.getRefreshToken(), client.getClientId());
        OAuth2AccessToken accessToken = jwtIssuer.issue(
                rotation.authorization(),
                rotation.sessionId(),
                client.getClientId(),
                rotation.issuedAt(),
                rotation.accessExpiresAt(),
                client.getScopes());
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                rotation.refreshToken(),
                rotation.issuedAt(),
                rotation.absoluteExpiresAt());
        return new OAuth2AccessTokenAuthenticationToken(
                client, clientPrincipal, accessToken, refreshToken);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return IamRefreshGrantAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static OAuth2ClientAuthenticationToken authenticatedClient(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OAuth2ClientAuthenticationToken client
                && client.isAuthenticated()) {
            return client;
        }
        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }
}
