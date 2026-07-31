package io.github.chrisshi.mom.system.domain.preference;

/**
 * 偏好领域输入校验失败。
 *
 * <p>稳定 code 供 Web 映射 400/413；异常不包含原始 Filter Value、Token、SQL 或数据库信息。</p>
 */
public final class PreferenceValidationException extends IllegalArgumentException {
    private final String code;

    public PreferenceValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public PreferenceValidationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** 返回客户端可稳定分支的机器码。 */
    public String code() {
        return code;
    }
}
