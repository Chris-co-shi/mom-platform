package io.github.chrisshi.mom.openfeign.autoconfigure;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.core.context.CorrelationHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class MomFeignAutoConfigurationTest {

    private final MomFeignAutoConfiguration configuration = new MomFeignAutoConfiguration();

    @AfterEach
    void cleanup() {
        CorrelationContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldPropagateCorrelationId() {
        CorrelationContext.set("corr-1001");
        RequestTemplate template = new RequestTemplate();

        configuration.momCorrelationIdFeignRequestInterceptor().apply(template);

        assertThat(template.headers().get(CorrelationHeaders.CORRELATION_ID))
                .containsExactly("corr-1001");
    }

    @Test
    void shouldPropagateBearerAuthorizationFromCurrentServletRequest() {
        bindRequest("Bearer user-token");
        RequestTemplate template = new RequestTemplate();

        configuration.momAuthorizationFeignRequestInterceptor().apply(template);

        assertThat(template.headers().get(HttpHeaders.AUTHORIZATION))
                .containsExactly("Bearer user-token");
    }

    @Test
    void shouldNotAddAuthorizationWithoutCurrentServletRequest() {
        RequestTemplate template = new RequestTemplate();

        configuration.momAuthorizationFeignRequestInterceptor().apply(template);

        assertThat(template.headers()).doesNotContainKey(HttpHeaders.AUTHORIZATION);
    }

    @Test
    void shouldNotPropagateNonBearerAuthorization() {
        bindRequest("Basic dXNlcjpwYXNz");
        RequestTemplate template = new RequestTemplate();

        configuration.momAuthorizationFeignRequestInterceptor().apply(template);

        assertThat(template.headers()).doesNotContainKey(HttpHeaders.AUTHORIZATION);
    }

    @Test
    void shouldNotOverwriteExplicitAuthorization() {
        bindRequest("Bearer inbound-token");
        RequestTemplate template = new RequestTemplate();
        template.header(HttpHeaders.AUTHORIZATION, "Bearer explicit-token");

        configuration.momAuthorizationFeignRequestInterceptor().apply(template);

        assertThat(template.headers().get(HttpHeaders.AUTHORIZATION))
                .containsExactly("Bearer explicit-token");
    }

    private static void bindRequest(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
