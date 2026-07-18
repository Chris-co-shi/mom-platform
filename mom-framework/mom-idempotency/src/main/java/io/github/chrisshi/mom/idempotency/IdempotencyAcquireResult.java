package io.github.chrisshi.mom.idempotency;

import java.time.Duration;

/**
 * 幂等占位操作的结构化返回结果。
 *
 * @param status 幂等占位状态
 * @param protectedKey 已脱敏的 Redis Key；原始业务幂等值会先进行 SHA-256 摘要，避免敏感数据进入 Key
 * @param ttl 幂等键生存时间；仅表示本次占位使用的超时时间
 * @param failureReason fail-open 绕过保护时的失败摘要；正常取得或重复时为空
 */
public record IdempotencyAcquireResult(
        IdempotencyAcquireStatus status,
        String protectedKey,
        Duration ttl,
        String failureReason) {

    /**
     * 判断当前请求是否取得业务执行资格。
     *
     * <p>fail-open 场景返回 {@code true}，但调用方必须通过 {@link #status()} 区分
     * {@link IdempotencyAcquireStatus#BYPASSED}，并记录审计或告警，不能把绕过保护误认为正常占位成功。</p>
     *
     * @return 首次取得占位或按策略绕过保护时返回 {@code true}
     */
    public boolean mayProceed() {
        return status == IdempotencyAcquireStatus.ACQUIRED
                || status == IdempotencyAcquireStatus.BYPASSED;
    }
}
