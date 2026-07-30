package io.github.chrisshi.mom.system.application.dictionary;

/**
 * System Dictionary 可公开映射的稳定用例错误。
 *
 * <p>异常不携带 SQL、约束名、Code 值以外的数据或认证信息。Infrastructure 只转换可识别的唯一冲突；
 * 其他数据库不可用错误保持失败，不伪装成 Not Found。</p>
 */
public final class SystemDictionaryException {
    private SystemDictionaryException() {
    }

    /** 字典或条目不存在。 */
    public static final class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** 稳定 Code 唯一性或关系状态冲突。 */
    public static final class Conflict extends RuntimeException {
        public Conflict(String message) {
            super(message);
        }

        public Conflict(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 客户端提交的乐观版本已过期。 */
    public static final class StaleVersion extends RuntimeException {
        public StaleVersion(String message) {
            super(message);
        }
    }
}
