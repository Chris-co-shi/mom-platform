package io.github.chrisshi.mom.iam.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Token Endpoint 成功响应；Refresh 明文只写入本次 HTTPS 响应，绝不持久化或记录日志。 */
public final class IamTokenResponseHandler implements AuthenticationSuccessHandler {
    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2AccessTokenAuthenticationToken tokenAuthentication =
                (OAuth2AccessTokenAuthenticationToken) authentication;
        OAuth2AccessToken accessToken = tokenAuthentication.getAccessToken();
        OAuth2RefreshToken returnedRefresh = tokenAuthentication.getRefreshToken();
        String refreshToken = returnedRefresh == null
                ? stringAttribute(request, IamSessionTokenService.REQUEST_REFRESH_TOKEN_ATTRIBUTE)
                : returnedRefresh.getTokenValue();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("access_token", accessToken.getTokenValue());
        payload.put("token_type", accessToken.getTokenType().getValue());
        Instant expiresAt = accessToken.getExpiresAt();
        long expiresIn = expiresAt == null ? 0L
                : Math.max(0L, Duration.between(Instant.now(), expiresAt).toSeconds());
        payload.put("expires_in", expiresIn);
        if (!accessToken.getScopes().isEmpty()) {
            payload.put("scope", String.join(" ", accessToken.getScopes()));
        }
        if (refreshToken != null) {
            payload.put("refresh_token", refreshToken);
        }
        tokenAuthentication.getAdditionalParameters().forEach((name, value) -> {
            if (!payload.containsKey(name) && value != null) {
                payload.put(name, responseValue(value));
            }
        });

        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.getWriter().write(json(payload));
    }

    private static Object responseValue(Object value) {
        if (value instanceof AbstractOAuth2Token token) {
            return token.getTokenValue();
        }
        if (value instanceof CharSequence) {
            return value.toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        throw new IllegalStateException("Token 响应包含不支持的附加参数类型");
    }

    private static String stringAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String text ? text : null;
    }

    private static String json(Map<String, Object> values) {
        StringBuilder result = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) result.append(',');
            first = false;
            result.append('"').append(escape(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                result.append(value);
            }
            else {
                result.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        return result.append('}').toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
