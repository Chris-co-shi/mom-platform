package io.github.chrisshi.mom.system.application.catalog;

/**
 * Application Catalog 的稳定应用错误族。
 *
 * <p>该类型只表达 Not Found、Conflict 与乐观锁失败，不暴露 SQL、约束名、表名或底层异常文本。Web
 * Adapter 将错误码映射为稳定 HTTP 响应；基础设施不可用时异常继续失败并触发本地事务回滚。</p>
 */
public abstract class SystemCatalogException extends RuntimeException {
    private final String code;

    protected SystemCatalogException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected SystemCatalogException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** 稳定业务错误码。 */
    public String code() {
        return code;
    }

    /** Application、Navigation 或 Release 不存在。 */
    public static final class NotFound extends SystemCatalogException {
        public NotFound(String code, String message) {
            super(code, message);
        }
    }

    /** 唯一、No-op、发布完整性或状态冲突。 */
    public static final class Conflict extends SystemCatalogException {
        public Conflict(String code, String message) {
            super(code, message);
        }

        public Conflict(String code, String message, Throwable cause) {
            super(code, message, cause);
        }
    }

    /** Application 或 Navigation Version 已陈旧。 */
    public static final class StaleVersion extends SystemCatalogException {
        public StaleVersion(String message) {
            super("stale_version", message);
        }
    }
}
