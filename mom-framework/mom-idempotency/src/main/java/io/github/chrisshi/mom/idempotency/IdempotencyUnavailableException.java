package io.github.chrisshi.mom.idempotency;

/**
 * Redis 幂等基础设施不可用异常。
 *
 * <p>当失败策略为 fail-closed 时，Redis 连接、超时或命令执行异常会转换为本异常。
 * 业务入口应将其映射为可重试的 503 或等价错误，禁止继续执行可能产生重复副作用的业务逻辑。</p>
 */
public final class IdempotencyUnavailableException extends RuntimeException {

    /**
     * 创建幂等基础设施不可用异常。
     *
     * @param message 面向日志和上层错误映射的中文或可读错误描述
     * @param cause 原始 Redis 访问异常
     */
    public IdempotencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
