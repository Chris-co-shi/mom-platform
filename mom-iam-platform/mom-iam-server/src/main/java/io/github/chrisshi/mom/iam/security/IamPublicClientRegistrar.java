package io.github.chrisshi.mom.iam.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;

/** 将四个固定 Public Client 幂等同步到官方 JDBC Registered Client Store。 */
final class IamPublicClientRegistrar implements ApplicationRunner {
    private final RegisteredClientRepository repository;
    private final IamAuthorizationProperties properties;

    IamPublicClientRegistrar(
            RegisteredClientRepository repository,
            IamAuthorizationProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        properties.validate();
        for (IamAuthorizationProperties.ClientRegistration registration : properties.registrations()) {
            RegisteredClient existing = repository.findByClientId(registration.clientId());
            String id = existing == null ? registration.clientId() : existing.getId();
            repository.save(publicClient(id, registration));
        }
    }

    private static RegisteredClient publicClient(
            String id, IamAuthorizationProperties.ClientRegistration registration) {
        return RegisteredClient.withId(id)
                .clientId(registration.clientId())
                .clientName(registration.clientName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(registration.client().getRedirectUri().toString())
                .postLogoutRedirectUri(registration.client().getPostLogoutRedirectUri().toString())
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(Duration.ofMinutes(5))
                        .accessTokenTimeToLive(Duration.ofMinutes(10))
                        .reuseRefreshTokens(false)
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                        .build())
                .build();
    }
}
