package io.github.chrisshi.mom.idempotency;

/**
 * 幂等占位的业务结果状态。
 *
 * <p>该枚举只描述“本次请求是否取得幂等执行资格”，不描述业务执行是否成功。
 * 幂等键一旦成功写入，会持续到 TTL 到期，调用方不得把它当作需要主动释放的分布式锁。</p>
 */
public enum IdempotencyAcquireStatus {

    /**
     * 当前请求首次写入幂等键，可以继续执行业务。
     */
    ACQUIRED,

    /**
     * 相同幂等键已经存在，当前请求应按重复请求处理。
     */
    DUPLICATE,

    /**
     * Redis 不可用且配置为 fail-open，当前请求绕过了幂等保护。
     */
    BYPASSED
}
