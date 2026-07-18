package io.github.chrisshi.mom.tracing;

/**
 * 当前执行线程或响应式上下文中的 Trace 标识快照。
 *
 * <p>该类型位于 {@code mom-tracing}，只暴露平台诊断需要的稳定字符串，不暴露 OpenTelemetry SDK、Span 或
 * Context 类型。快照不可变，可安全用于响应 DTO、结构化日志和自动化验收；它不应作为业务主键、幂等键或
 * 数据库关联关系。</p>
 *
 * @param traceId 当前 Trace 标识；没有活动 Span 时为空字符串
 * @param spanId 当前 Span 标识；没有活动 Span 时为空字符串
 */
public record TraceContextSnapshot(String traceId, String spanId) {

    /**
     * 表示当前执行位置没有活动 Span 的空快照。
     */
    public static final TraceContextSnapshot EMPTY = new TraceContextSnapshot("", "");

    /**
     * 判断快照是否包含可用 Trace 与 Span 标识。
     *
     * @return 两个标识都非空时返回 {@code true}
     */
    public boolean isPresent() {
        return !traceId.isBlank() && !spanId.isBlank();
    }
}
