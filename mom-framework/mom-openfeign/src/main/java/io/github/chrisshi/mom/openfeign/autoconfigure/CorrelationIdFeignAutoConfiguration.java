package io.github.chrisshi.mom.openfeign.autoconfigure;

import feign.RequestInterceptor;
import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.core.context.CorrelationHeaders;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class CorrelationIdFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "momCorrelationIdFeignRequestInterceptor")
    RequestInterceptor momCorrelationIdFeignRequestInterceptor() {
        return template -> {
            String correlationId = CorrelationContext.currentId();
            if (correlationId != null && !correlationId.isBlank()) {
                template.header(CorrelationHeaders.CORRELATION_ID, correlationId);
            }
        };
    }
}
