package io.github.chrisshi.mom.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/**
 * 基于 Micrometer Tracing 读取当前 Trace 上下文的稳定平台入口。
 *
 * <p>领域服务依赖该类型而不是 OpenTelemetry SDK，从而保留后端替换能力。读取操作无副作用，也不会创建
 * 新 Span；当前执行位置没有活动 Span 或当前测试上下文未创建 Tracer 时返回空快照，避免可观测性基础设施
 * 缺失直接破坏数据库迁移、离线任务或业务请求。</p>
 *
 * <p>该类型不保存线程状态，实际上下文生命周期由 Micrometer 与底层 Servlet、Reactor、Feign 或消息
 * Instrumentation 管理。实例可安全地作为单例 Bean 共享。</p>
 */
public final class CurrentTraceContext {

    private final Tracer tracer;

    /**
     * 创建当前 Trace 上下文访问器。
     *
     * @param tracer Micrometer Tracer 门面；允许为空以支持不启用追踪的离线上下文
     */
    public CurrentTraceContext(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * 读取当前活动 Span 的 Trace 与 Span 标识。
     *
     * @return 不可变标识快照；没有 Tracer 或活动 Span 时返回 {@link TraceContextSnapshot#EMPTY}
     */
    public TraceContextSnapshot snapshot() {
        if (tracer == null) {
            return TraceContextSnapshot.EMPTY;
        }
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null || currentSpan.context() == null) {
            return TraceContextSnapshot.EMPTY;
        }
        String traceId = currentSpan.context().traceId();
        String spanId = currentSpan.context().spanId();
        if (traceId == null || traceId.isBlank() || spanId == null || spanId.isBlank()) {
            return TraceContextSnapshot.EMPTY;
        }
        return new TraceContextSnapshot(traceId, spanId);
    }
}
