package io.github.chrisshi.mom.core.security;

import java.util.Optional;

/**
 * 当前线程显式审计操作人的最小上下文。
 *
 * <p>使用普通 {@link ThreadLocal}，不会自动继承到子线程、线程池、虚拟线程或 Reactor 链路。异步任务必须
 * 显式传递 {@link AuditActor}，后台任务必须通过 {@link AuditContextExecutor#runAsSystem} 建立身份。</p>
 */
public final class AuditContext {

    private static final ThreadLocal<AuditActor> CURRENT = new ThreadLocal<>();

    private AuditContext() {
    }

    /**
     * 读取当前线程显式绑定的操作人。
     *
     * @return 当前 Actor；未绑定时返回空
     */
    public static Optional<AuditActor> findCurrentActor() {
        return Optional.ofNullable(CURRENT.get());
    }

    static AuditActor bind(AuditActor actor) {
        AuditActor previous = CURRENT.get();
        CURRENT.set(actor);
        return previous;
    }

    static void restore(AuditActor previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    static void clear() {
        CURRENT.remove();
    }
}
