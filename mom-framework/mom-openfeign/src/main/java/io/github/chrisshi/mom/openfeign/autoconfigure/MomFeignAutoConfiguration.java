package io.github.chrisshi.mom.openfeign.autoconfigure;

import feign.RequestInterceptor;
import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.core.context.CorrelationHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * MOM 内部同步 OpenFeign 调用的全局上下文传播配置。
 *
 * <p>该模块只用于 MOM 内部服务间同步 HTTP RPC。SAP、LIMS、PCS、AGV 等第三方或外部系统调用
 * 不得复用 {@code mom-openfeign}，避免将当前用户的 Authorization 凭据发送到外部边界。</p>
 *
 * <p>当前仅传播 {@code X-Correlation-Id} 与原始 Bearer Authorization。没有当前 Servlet 请求时，
 * Authorization 不传播，也不会自动创建系统身份或系统 Token。后台任务、消息消费和其他机器调用
 * 如后续出现真实跨服务认证需求，再单独扩展 SYSTEM/SERVICE 身份模型。</p>
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MomFeignAutoConfiguration {

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

    @Bean
    @ConditionalOnMissingBean(name = "momAuthorizationFeignRequestInterceptor")
    RequestInterceptor momAuthorizationFeignRequestInterceptor() {
        return template -> {
            if (hasAuthorizationHeader(template.headers())) {
                return;
            }

            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
                return;
            }

            HttpServletRequest request = attributes.getRequest();
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (isBearerAuthorization(authorization)) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        };
    }

    private static boolean hasAuthorizationHeader(java.util.Map<String, ? extends java.util.Collection<String>> headers) {
        return headers.keySet().stream().anyMatch(HttpHeaders.AUTHORIZATION::equalsIgnoreCase);
    }

    private static boolean isBearerAuthorization(String authorization) {
        if (authorization == null || authorization.length() <= 7) {
            return false;
        }
        return authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                && !authorization.substring(7).isBlank();
    }
}
