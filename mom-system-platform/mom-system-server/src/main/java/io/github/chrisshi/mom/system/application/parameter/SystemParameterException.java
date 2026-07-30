package io.github.chrisshi.mom.system.application.parameter;

/**
 * System Parameter 可公开映射的用例错误集合。
 *
 * <p>异常位于 Application 边界，使 Web 不需要穿透 Domain。异常只包含稳定业务语义，不携带 SQL、参数
 * 值或认证信息；Infrastructure 唯一约束错误可转换为 Conflict，其他不可用错误不降级。</p>
 */
public final class SystemParameterException {
    private SystemParameterException() {
    }

    /** 参数不存在。 */
    public static final class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    /** 唯一性、类型一致性或状态发生冲突。 */
    public static final class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
        public Conflict(String message, Throwable cause) { super(message, cause); }
    }

    /** 客户端版本已过期。 */
    public static final class StaleVersion extends RuntimeException {
        public StaleVersion(String message) { super(message); }
    }
}
