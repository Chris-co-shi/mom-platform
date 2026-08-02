package io.github.chrisshi.mom.security.revocation;

/**
 * revoked sid 权威数据源不可用或结果不确定。
 *
 * <p>该异常只表达安全基础设施失败，不包含 Redis 地址、Key、凭据或底层命令；Servlet Resource Server
 * 将其映射为 HTTP 503 并 Fail Closed。异常不得被业务代码转换为“未撤销”或业务成功。</p>
 */
public final class MomRevocationStoreUnavailableException extends RuntimeException {

    /** 使用脱敏消息创建异常。 */
    public MomRevocationStoreUnavailableException(String message) {
        super(message);
    }

    /** 使用固定脱敏消息和底层原因创建异常。 */
    public MomRevocationStoreUnavailableException(Throwable cause) {
        super("revoked sid store unavailable", cause);
    }
}
