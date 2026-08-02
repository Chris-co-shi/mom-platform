package io.github.chrisshi.mom.iam.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** System client_credentials 服务身份注册测试。 */
class IamSystemServiceClientRegistrarTest {

    @Test
    void mustRegisterHashedClientCredentialsWithoutRefreshToken() {
        RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("01234567890123456789012345678901")).thenReturn("{bcrypt}encoded");
        IamAuthorizationProperties properties = new IamAuthorizationProperties();
        var service = properties.getSystemService();
        service.setEnabled(true);
        service.setClientSecret("01234567890123456789012345678901");
        service.setAccessTokenTtl(Duration.ofMinutes(3));

        new IamSystemServiceClientRegistrar(repository, properties, encoder).run(null);

        ArgumentCaptor<RegisteredClient> captor = ArgumentCaptor.forClass(RegisteredClient.class);
        verify(repository).save(captor.capture());
        RegisteredClient registered = captor.getValue();
        assertThat(registered.getClientId()).isEqualTo("mom-system-server");
        assertThat(registered.getClientSecret()).isEqualTo("{bcrypt}encoded");
        assertThat(registered.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(registered.getAuthorizationGrantTypes())
                .containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(registered.getScopes()).containsExactly("iam.permission-reference.read");
        assertThat(registered.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(Duration.ofMinutes(3));
        assertThat(registered.getTokenSettings().isReuseRefreshTokens()).isFalse();
    }
}
