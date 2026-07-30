package io.github.chrisshi.mom.system.application.i18n;

/** Dynamic I18n 应用错误族，用于 Web 稳定映射 404/409。 */
public abstract class SystemI18nException extends RuntimeException {
    protected SystemI18nException(String message) {
        super(message);
    }

    protected SystemI18nException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 资源、草稿或完整发布版本不存在。 */
    public static final class NotFound extends SystemI18nException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** 唯一冲突、No-op 或发布状态冲突。 */
    public static final class Conflict extends SystemI18nException {
        public Conflict(String message) {
            super(message);
        }

        public Conflict(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 客户端资源或草稿 Version 已陈旧。 */
    public static final class StaleVersion extends SystemI18nException {
        public StaleVersion(String message) {
            super(message);
        }
    }
}
