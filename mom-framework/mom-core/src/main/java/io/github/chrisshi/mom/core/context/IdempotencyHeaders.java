package io.github.chrisshi.mom.core.context;

/**
 * MOM 平台统一使用的幂等 HTTP Header 常量。
 *
 * <p>该类型属于 mom-core 的同步 HTTP 契约，只允许上层 Web 或 Client 依赖，不依赖 Servlet、Redis 或
 * 具体业务模块。{@code Idempotency-Key} 由调用方为一次业务意图生成，同一业务意图重试时必须原样复用，
 * 不同业务意图不得复用；原始值不得写入 Redis Key 或日志。类型无状态、线程安全，基础设施故障策略由
 * 实际幂等组件决定。</p>
 */
public final class IdempotencyHeaders {

    /** 客户端或上游系统提交的幂等标识 Header 名称。 */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private IdempotencyHeaders() {
    }
}
