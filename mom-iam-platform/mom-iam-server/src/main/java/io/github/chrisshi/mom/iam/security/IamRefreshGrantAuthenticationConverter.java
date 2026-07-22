package io.github.chrisshi.mom.iam.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.Map;

/** 在默认 Refresh Converter 前解析 MOM 自有摘要轮换请求。 */
public final class IamRefreshGrantAuthenticationConverter implements AuthenticationConverter {
    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(
                request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }
        String[] values = request.getParameterValues(OAuth2ParameterNames.REFRESH_TOKEN);
        if (values == null || values.length != 1 || !StringUtils.hasText(values[0])) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        return new IamRefreshGrantAuthenticationToken(values[0], clientPrincipal, Map.of());
    }
}
