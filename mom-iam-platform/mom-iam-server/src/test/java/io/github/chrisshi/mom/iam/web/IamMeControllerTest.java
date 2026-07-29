package io.github.chrisshi.mom.iam.web;

import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.security.IamAuthorizationContext;
import io.github.chrisshi.mom.iam.security.IamAuthorizationContextLoader;
import io.github.chrisshi.mom.iam.security.IamClientAccessPolicyService;
import io.github.chrisshi.mom.iam.security.IamScopeGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** `/api/iam/me` 权威上下文、Client 与当前工厂行为特征测试。 */
@ExtendWith(MockitoExtension.class)
class IamMeControllerTest {
    @Mock IamAuthorizationContextLoader contexts;
    @Mock IamClientAccessPolicyService clientAccess;
    @Mock IamScopeGuard scopeGuard;

    /** 已验证 JWT 必须按 sub 重新加载权威数据，并原样保持响应字段语义。 */
    @Test
    void meMustReloadAuthoritativeContextAndValidateCurrentFactory() {
        IamAuthorizationContext context = new IamAuthorizationContext(
                "user-1", "admin", "Administrator", UserType.INTERNAL,
                List.of("MOM_ADMIN"), List.of("iam:user:read"), List.of("factory-1"),
                null, null);
        when(contexts.loadByUserId("user-1")).thenReturn(context);
        when(scopeGuard.requireCurrentFactory(context, "factory-1")).thenReturn("factory-1");
        IamMeController controller = new IamMeController(contexts, clientAccess, scopeGuard);

        IamMeController.IamMeResponse response = controller.me(
                new JwtAuthenticationToken(
                        jwt("mom-admin-web"), List.of(new SimpleGrantedAuthority("ROLE_USER"))),
                "factory-1");

        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.userType()).isEqualTo("INTERNAL");
        assertThat(response.clientId()).isEqualTo("mom-admin-web");
        assertThat(response.sid()).isEqualTo("session-1");
        assertThat(response.roles()).containsExactly("MOM_ADMIN");
        assertThat(response.permissions()).containsExactly("iam:user:read");
        assertThat(response.factoryIds()).containsExactly("factory-1");
        assertThat(response.currentFactoryId()).isEqualTo("factory-1");
        assertThat(response.mobileAccessEnabled()).isFalse();
        verify(contexts).loadByUserId("user-1");
    }

    /** 缺少认证不得降级为匿名上下文或信任请求字段。 */
    @Test
    void meMustRejectMissingAuthentication() {
        IamMeController controller = new IamMeController(contexts, clientAccess, scopeGuard);

        assertThatThrownBy(() -> controller.me(null, "factory-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("缺少已认证用户");
    }

    private static Jwt jwt(String clientId) {
        Instant issuedAt = Instant.parse("2026-07-29T04:00:00Z");
        return Jwt.withTokenValue("access-1")
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(600))
                .claim("client_id", clientId)
                .claim("sid", "session-1")
                .build();
    }
}
