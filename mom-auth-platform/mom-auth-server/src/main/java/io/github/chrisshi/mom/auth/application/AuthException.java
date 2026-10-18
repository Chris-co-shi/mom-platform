package io.github.chrisshi.mom.auth.application;

/** Auth Application 层向 HTTP 边界传递的可分类业务异常。 */
public final class AuthException extends RuntimeException {

    /** 稳定错误分类，供异常处理器映射为 API 错误码。 */
    private final AuthErrorCode errorCode;

    /**
     * @param errorCode Auth 错误分类
     */
    public AuthException(AuthErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null);
    }

    /**
     * @param errorCode Auth 错误分类
     * @param detail 面向日志或响应映射的补充详情
     */
    public AuthException(AuthErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    /**
     * @param errorCode Auth 错误分类
     * @param detail 补充详情
     * @param cause 原始异常
     */
    public AuthException(AuthErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
    }

    /**
     * @return 稳定 Auth 错误分类
     */
    public AuthErrorCode errorCode() {
        return errorCode;
    }
}
