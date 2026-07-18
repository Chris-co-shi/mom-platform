package io.github.chrisshi.mom.webmvc.context;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.core.context.CorrelationHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet 请求关联标识 Filter。
 *
 * <p>每个请求只执行一次：优先复用上游 {@code X-Correlation-Id}，缺失时生成 UUID；随后把标识
 * 写入响应 Header 和当前线程上下文，使日志、Feign 和幂等组件能够读取同一值。</p>
 *
 * <p>Filter 必须在 {@code finally} 中清理 ThreadLocal。Servlet 容器会复用工作线程，如果遗漏清理，
 * 后续无关请求可能继承上一请求的关联标识，造成日志串线和错误审计归属。</p>
 */
public final class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    /**
     * 为当前请求建立并释放关联标识上下文。
     *
     * @param request Servlet 请求
     * @param response Servlet 响应
     * @param filterChain 后续过滤器链
     * @throws ServletException 后续 Filter 或 Controller 抛出的 Servlet 异常
     * @throws IOException 响应写入或过滤链处理时的 I/O 异常
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = CorrelationContext.resolveOrGenerate(
                request.getHeader(CorrelationHeaders.CORRELATION_ID));

        CorrelationContext.set(correlationId);
        response.setHeader(CorrelationHeaders.CORRELATION_ID, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
        }
    }

    /**
     * 让关联标识在安全、审计和业务 Filter 之前建立，同时保留更高优先级给容器级关键 Filter。
     *
     * @return Filter 排序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
