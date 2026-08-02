package io.github.chrisshi.mom.system.web.preference;

import io.github.chrisshi.mom.system.application.preference.CurrentPreferenceUserProvider;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 从已验证 Resource Server JWT 解析当前偏好用户的 Web 安全 Adapter。
 *
 * <p>只接受 JwtAuthenticationToken 的 sub，不读取 URL、Body、自报 Header、username 或 IAM Repository。
 * 缺少认证、非 JWT 或 sub 不满足 String ID 约束时 fail closed。</p>
 */
@Component
public class SecurityContextCurrentPreferenceUserProvider implements CurrentPreferenceUserProvider {
    @Override
    public String requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !authentication.isAuthenticated()) {
            throw new SystemUserPreferenceException.NotAuthenticated();
        }
        String subject = token.getToken().getSubject();
        if (subject == null || subject.isBlank() || subject.length() > 19 || !subject.equals(subject.trim())) {
            throw new SystemUserPreferenceException.NotAuthenticated();
        }
        return subject;
    }
}
