package io.github.chrisshi.mom.idempotency;

import java.time.Duration;

/**
 * 面向业务入口的幂等占位接口。
 *
 * <p>接口只负责通过原子写入判断某个请求是否首次到达，不负责回滚业务事务，也不负责保存最终业务响应。
 * 调用方必须在执行业务副作用之前调用本接口，并为不同业务动作设置稳定且隔离的 scope。</p>
 */
public interface IdempotencyGuard {

    /**
     * 尝试取得幂等执行资格。
     *
     * @param scope 业务动作作用域，例如 {@code integration.delivery.receive}；不能为空
     * @param requestKey 客户端或上游提供的原始幂等标识；不会直接写入 Redis Key
     * @param ownerToken 当前请求持有者标识，建议使用关联标识，便于排障
     * @param ttl 幂等占位生存时间；必须大于零，并覆盖业务最长可接受重试窗口
     * @return 结构化占位结果
     * @throws IllegalArgumentException 参数为空、TTL 非法或 scope 不符合约束时抛出
     * @throws IdempotencyUnavailableException Redis 不可用且失败策略为 fail-closed 时抛出
     */
    IdempotencyAcquireResult tryAcquire(
            String scope,
            String requestKey,
            String ownerToken,
            Duration ttl);
}
