package io.github.chrisshi.mom.iam.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 解析 Public Client 的 Refresh Grant 身份。
 *
 * <p>Spring 内置 Public Client Converter 只接受带 code_verifier 的授权码换 Token；Refresh 请求没有
 * code_verifier，因此本转换器仅在 grant_type=refresh_token 时读取唯一 client_id。Refresh Token 明文
 * 不进入 Client Authentication additional parameters。</p>
 */
public final class IamPublicRefreshClientAuthenticationConverter implements AuthenticationConverter {
    static final String REFRESH_GRANT_MARKER =
            IamPublicRefreshClientAuthenticationConverter.class.getName() + ".refreshGrant";

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(
                request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }
        String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
        if (clientIds == null || clientIds.length != 1 || !StringUtils.hasText(clientIds[0])) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        return new OAuth2ClientAuthenticationToken(
                clientIds[0],
                ClientAuthenticationMethod.NONE,
                null,
                Map.of(REFRESH_GRANT_MARKER, Boolean.TRUE));
    }
}
