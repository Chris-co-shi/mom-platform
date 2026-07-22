package io.github.chrisshi.mom.iam.admin;

/**
 * S07 管理 API 的稳定领域异常集合。
 *
 * <p>异常只携带可公开的稳定语义，不携带 SQL、请求正文或凭证。事务服务抛出运行时异常后由
 * Spring 回滚关系、父聚合版本和成功审计，避免并发失败留下部分副作用。</p>
 */
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

    /** 客户端提交的读取版本不是父聚合当前版本。 */
    public static final class StaleVersion extends RuntimeException {
        public StaleVersion(String message) { super(message); }
    }

    /** 外部依赖或权威校验端口当前不可用，必须 Fail Closed。 */
    public static final class DependencyUnavailable extends RuntimeException {
        public DependencyUnavailable(String message) { super(message); }
    }
}
