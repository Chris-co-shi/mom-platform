package io.github.chrisshi.mom.iam.admin;

/** S07 管理 API 的稳定领域异常集合。 */
public final class IamAdminExceptions {
    private IamAdminExceptions() {
    }

    /** 目标不存在或已逻辑删除。 */
    public static final class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    /** 操作违反并发、安全或冻结协议约束。 */
    public static final class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
    }

    /** 外部依赖或权威校验端口当前不可用，必须 Fail Closed。 */
    public static final class DependencyUnavailable extends RuntimeException {
        public DependencyUnavailable(String message) { super(message); }
    }
}
