package io.github.chrisshi.mom.gateway.error;

import org.springframework.http.HttpStatus;

/**
 * Gateway 对外稳定错误定义。
 *
 * <p>code 面向调用方保持稳定；messageKey 用于国际化解析；defaultMessage 只作为消息资源缺失时的中文兜底。</p>
 */
public enum GatewayErrorCode {

    MISSING_BEARER_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "missing_bearer_token",
            "gateway.error.missing-bearer-token",
            "缺少 Bearer Token"),

    INVALID_BEARER_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "invalid_bearer_token",
            "gateway.error.invalid-bearer-token",
            "Bearer Token 格式非法"),

    RATE_LIMIT_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "rate_limit_unavailable",
            "gateway.error.rate-limit-unavailable",
            "网关限流基础设施暂时不可用");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    GatewayErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String messageKey() {
        return messageKey;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
