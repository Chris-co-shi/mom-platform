package io.github.chrisshi.mom.core.context;

import java.util.UUID;

/**
 * 同步调用链的请求关联标识上下文。
 *
 * <p>该类型使用 {@link ThreadLocal} 保存当前 Servlet 请求的关联标识，供日志、审计、Feign 拦截器
 * 和基础设施组件读取。生命周期由 WebMVC 请求 Filter 负责：请求进入时写入，请求结束时必须在
 * {@code finally} 中清理，避免线程池复用导致不同用户请求串线。</p>
 *
 * <p>该上下文只适用于同一线程内的同步调用。WebFlux、异步线程池和消息消费场景不得直接依赖本
 * ThreadLocal，必须使用 Reactor Context、显式参数或消息 Header 传播。</p>
 */
public final class CorrelationContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationContext() {
    }

    /**
     * 使用上游关联标识，缺失时生成新的 UUID。
     *
     * @param candidate 上游 Header 或调用方传入的候选值
     * @return 去除首尾空白后的关联标识；候选值为空时返回新 UUID
     */
    public static String resolveOrGenerate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return candidate.trim();
    }

    /**
     * 将关联标识绑定到当前线程。
     *
     * @param correlationId 关联标识；为空时自动生成
     */
    public static void set(String correlationId) {
        CURRENT.set(resolveOrGenerate(correlationId));
    }

    /**
     * 获取当前线程绑定的关联标识。
     *
     * @return 当前关联标识；尚未绑定时返回 {@code null}
     */
    public static String currentId() {
        return CURRENT.get();
    }

    /**
     * 清理当前线程的关联标识。
     *
     * <p>Servlet Filter 必须无条件调用该方法，不能仅在请求成功时清理。</p>
     */
    public static void clear() {
        CURRENT.remove();
    }
}
