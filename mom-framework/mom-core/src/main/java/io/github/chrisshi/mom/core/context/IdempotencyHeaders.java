package io.github.chrisshi.mom.core.context;

/**
 * MOM 平台统一使用的幂等 HTTP Header。
 *
 * <p>{@code Idempotency-Key} 由调用方为一次业务意图生成。同一业务意图重试时必须复用同一个值，
 * 不同业务意图不得复用。平台不会把原始值直接写入 Redis Key，而是先计算不可逆摘要。</p>
 */
public final class IdempotencyHeaders {

    /**
     * 客户端或上游系统提交的幂等标识 Header。
     */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private IdempotencyHeaders() {
    }
}
