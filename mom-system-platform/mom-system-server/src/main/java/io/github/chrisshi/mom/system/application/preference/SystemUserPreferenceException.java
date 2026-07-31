package io.github.chrisshi.mom.system.application.preference;

/** System 用户偏好应用错误族；Web 只暴露稳定 code 和脱敏说明。 */
public abstract class SystemUserPreferenceException extends RuntimeException {
    private final String code;

    protected SystemUserPreferenceException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected SystemUserPreferenceException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** 返回稳定机器码。 */
    public String code() {
        return code;
    }

    /** 当前请求没有可信 JWT sub。 */
    public static final class NotAuthenticated extends SystemUserPreferenceException {
        public NotAuthenticated() {
            super("not_authenticated", "当前请求未认证");
        }
    }

    /** 数据库唯一竞争或乐观版本冲突。 */
    public static final class StaleVersion extends SystemUserPreferenceException {
        public StaleVersion() {
            super("stale_version", "偏好已被其他请求修改，请重新读取");
        }

        public StaleVersion(Throwable cause) {
            super("stale_version", "偏好已被其他请求修改，请重新读取", cause);
        }
    }

    /** 已由 Domain 判定的稳定输入错误；Web 不需要依赖 Domain 异常类型。 */
    public static final class Invalid extends SystemUserPreferenceException {
        public Invalid(String code, String message) {
            super(code, message);
        }
    }
}
