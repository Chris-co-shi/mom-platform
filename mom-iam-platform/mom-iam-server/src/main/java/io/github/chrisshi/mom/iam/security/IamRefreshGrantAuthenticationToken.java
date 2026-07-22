package io.github.chrisshi.mom.iam.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

import java.util.Map;

/** MOM 自有 Refresh Rotation Grant；明文只在当前请求对象内短暂存在。 */
public final class IamRefreshGrantAuthenticationToken
        extends OAuth2AuthorizationGrantAuthenticationToken {

    private final String refreshToken;

    public IamRefreshGrantAuthenticationToken(
            String refreshToken,
            Authentication clientPrincipal,
            Map<String, Object> additionalParameters) {
        super(AuthorizationGrantType.REFRESH_TOKEN, clientPrincipal, additionalParameters);
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
