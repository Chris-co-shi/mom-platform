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

public final class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
