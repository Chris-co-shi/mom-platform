package io.github.chrisshi.mom.iam.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

/**
 * 将受控的 mom-system-server client_credentials 服务身份幂等注册到 SAS JDBC Store。
 *
 * <p>Client Secret 仅从环境绑定属性读取并使用 IAM PasswordEncoder 哈希后保存；配置关闭时不注册。该服务身份
 * 只拥有 Permission Reference 只读 Scope，不获得用户 Role、Permission 或 Refresh Token。</p>
 */
final class IamSystemServiceClientRegistrar implements ApplicationRunner {
    private final RegisteredClientRepository repository;
    private final IamAuthorizationProperties properties;
    private final PasswordEncoder passwordEncoder;

    IamSystemServiceClientRegistrar(
            RegisteredClientRepository repository,
            IamAuthorizationProperties properties,
            PasswordEncoder passwordEncoder) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
    }

    @Override
    public void run(ApplicationArguments arguments) {
        var service = properties.getSystemService();
        if (!service.isEnabled()) {
            return;
        }
        service.validate();
        RegisteredClient existing = repository.findByClientId(service.getClientId());
        String id = existing == null ? service.getClientId() : existing.getId();
        repository.save(RegisteredClient.withId(id)
                .clientId(service.getClientId())
                .clientName(service.getClientName())
                .clientSecret(passwordEncoder.encode(service.getClientSecret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(service.getScope())
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(service.getAccessTokenTtl())
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .reuseRefreshTokens(false)
                        .build())
                .build());
    }
}
