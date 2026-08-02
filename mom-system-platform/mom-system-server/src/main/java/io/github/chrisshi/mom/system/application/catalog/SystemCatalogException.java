package io.github.chrisshi.mom.system.application.catalog;

/**
 * Application Catalog 的稳定应用错误族。
 *
 * <p>该类型表达 Not Found、Conflict、乐观锁与受控远程权威依赖错误，不暴露 SQL、约束名、表名、Token 或
 * 底层网络异常文本。Web Adapter 映射稳定 HTTP 响应；基础设施不可用时本地事务必须回滚。</p>
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

    /** IAM Permission 权威服务临时不可用或超时。 */
    public static final class DependencyUnavailable extends SystemCatalogException {
        public DependencyUnavailable(String message, Throwable cause) {
            super("permission_authority_unavailable", message, cause);
        }
    }

    /** IAM Permission 权威服务返回不符合稳定契约的响应。 */
    public static final class DependencyProtocol extends SystemCatalogException {
        public DependencyProtocol(String message) {
            super("permission_authority_protocol_error", message);
        }

        public DependencyProtocol(String message, Throwable cause) {
            super("permission_authority_protocol_error", message, cause);
        }
    }
}
