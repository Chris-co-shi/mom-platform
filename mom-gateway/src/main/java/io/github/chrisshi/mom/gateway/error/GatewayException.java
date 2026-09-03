package io.github.chrisshi.mom.gateway.error;

import java.util.Objects;

/** Gateway 可预期边缘异常，由统一 {@link GatewayExceptionHandler} 映射为 HTTP 错误响应。 */
public final class GatewayException extends RuntimeException {

    private final GatewayErrorCode errorCode;

    public GatewayException(GatewayErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode").code());
        this.errorCode = errorCode;
    }

    public GatewayException(GatewayErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode").code(), cause);
        this.errorCode = errorCode;
    }

    public GatewayErrorCode errorCode() {
        return errorCode;
    }
}
