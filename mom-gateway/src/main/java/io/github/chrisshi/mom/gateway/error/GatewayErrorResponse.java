package io.github.chrisshi.mom.gateway.error;

/** Gateway 对外错误响应。 */
public record GatewayErrorResponse(String code, String message) {
}
