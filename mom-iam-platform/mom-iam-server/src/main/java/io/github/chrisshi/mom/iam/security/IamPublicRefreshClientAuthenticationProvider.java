package io.github.chrisshi.mom.iam.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** 只认证允许 Refresh Grant 的无密钥 Public Client。 */
public final class IamPublicRefreshClientAuthenticationProvider implements AuthenticationProvider {
    private final RegisteredClientRepository clients;

    public IamPublicRefreshClientAuthenticationProvider(RegisteredClientRepository clients) {
        this.clients = clients;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2ClientAuthenticationToken candidate =
                (OAuth2ClientAuthenticationToken) authentication;
        if (!ClientAuthenticationMethod.NONE.equals(candidate.getClientAuthenticationMethod())
                || !Boolean.TRUE.equals(candidate.getAdditionalParameters().get(
                        IamPublicRefreshClientAuthenticationConverter.REFRESH_GRANT_MARKER))) {
            return null;
        }

        String clientId = candidate.getPrincipal().toString();
        RegisteredClient registeredClient = clients.findByClientId(clientId);
        if (registeredClient == null
                || !registeredClient.getClientAuthenticationMethods()
                        .contains(ClientAuthenticationMethod.NONE)
                || !registeredClient.getAuthorizationGrantTypes()
                        .contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            throw invalidClient();
        }
        return new OAuth2ClientAuthenticationToken(
                registeredClient,
                ClientAuthenticationMethod.NONE,
                null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static OAuth2AuthenticationException invalidClient() {
        return new OAuth2AuthenticationException(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_CLIENT,
                "Public Client 不允许执行 Refresh Grant",
                null));
    }
}
