package io.github.chrisshi.mom.openfeign.autoconfigure;

import feign.Target;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 OpenFeign CircuitBreaker 名称不受服务 URL 和请求参数影响。
 */
class CorrelationIdFeignAutoConfigurationTest {

    @Test
    void shouldResolveStableCircuitBreakerNameFromClientTypeAndMethod() throws NoSuchMethodException {
        CorrelationIdFeignAutoConfiguration configuration = new CorrelationIdFeignAutoConfiguration();
        CircuitBreakerNameResolver resolver = configuration.momFeignCircuitBreakerNameResolver();
        @SuppressWarnings("unchecked")
        Target<ExampleClient> target = mock(Target.class);
        when(target.type()).thenReturn(ExampleClient.class);
        Method method = ExampleClient.class.getMethod("validate", String.class);

        assertThat(resolver.resolveCircuitBreakerName("dynamic-service-name", target, method))
                .isEqualTo("ExampleClient_validate");
    }

    private interface ExampleClient {
        void validate(String value);
    }
}
