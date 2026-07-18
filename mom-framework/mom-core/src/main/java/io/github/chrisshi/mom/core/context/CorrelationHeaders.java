package io.github.chrisshi.mom.core.context;

/**
 * MOM 服务间同步调用使用的关联标识 Header 常量。
 *
 * <p>Gateway、Servlet 服务和 Feign Client 必须统一使用该常量，避免不同模块自行定义大小写或名称，
 * 导致日志链路无法关联。该标识用于排障和审计，不承担身份认证、授权或业务幂等职责。</p>
 */
public final class CorrelationHeaders {

    /**
     * 跨 Gateway 和内部服务传播的请求关联标识。
     */
    public static final String CORRELATION_ID = "X-Correlation-Id";

    private CorrelationHeaders() {
    }
}
