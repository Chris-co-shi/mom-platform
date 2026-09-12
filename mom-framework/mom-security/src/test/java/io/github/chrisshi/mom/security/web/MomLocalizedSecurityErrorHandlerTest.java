package io.github.chrisshi.mom.security.web;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security Filter Chain 独立异常入口的协议测试。
 *
 * <p>测试不启动网络和认证服务，只证明 401/403 在 ControllerAdvice 之外仍复用 Resolver，并且响应不泄露原始
 * Security 异常。Mock Servlet 响应与生产序列化器一致，测试无外部副作用。</p>
 */
class MomLocalizedSecurityErrorHandlerTest {

    /** 未认证响应应保留稳定 code，并按请求 Locale 解析展示文案。 */
    @Test
    void authenticationEntryPointMustUseSharedResolver() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addPreferredLocale(Locale.SIMPLIFIED_CHINESE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler().commence(request, response, new BadCredentialsException("sensitive-token-detail"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("AUTHENTICATION_REQUIRED", "zh-CN:framework.security.unauthorized")
                .doesNotContain("sensitive-token-detail");
    }

    /** 无权限响应应使用独立 403 code，且不依赖 MVC Exception Handler。 */
    @Test
    void accessDeniedHandlerMustUseSharedResolver() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addPreferredLocale(Locale.US);
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler().handle(request, response, new AccessDeniedException("internal-policy"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString())
                .contains("ACCESS_DENIED", "en-US:framework.security.access_denied")
                .doesNotContain("internal-policy");
    }

    /** 构造仅用于当前测试的确定性 Resolver，不访问 classpath、数据库或网络。 */
    private static MomLocalizedSecurityErrorHandler handler() {
        I18nMessageResolver resolver = (key, locale, args) -> locale.toLanguageTag() + ":" + key;
        return new MomLocalizedSecurityErrorHandler(resolver, new ObjectMapper());
    }
}
