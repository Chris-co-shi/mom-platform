package io.github.chrisshi.mom.core.security;

import io.github.chrisshi.mom.core.context.CorrelationContext;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 在当前线程显式建立 USER、ADMIN 或 SYSTEM 审计上下文的执行器。
 *
 * <p>所有入口都使用严格 {@code try/finally} 恢复外层上下文。嵌套执行、任务异常和线程池复用都不会把
 * 上一个任务的 Actor 泄漏给后续任务。该执行器不自动跨线程传播；需要代表用户异步执行时，调用方必须
 * 显式捕获并传递 {@link AuditActor}。</p>
 */
public final class AuditContextExecutor {

    private static final Pattern SYSTEM_ACTOR_CODE = Pattern.compile("[a-z][a-z0-9-]{2,127}");
    private static final Set<String> MEANINGLESS_CODES = Set.of("system", "default", "unknown");

    /**
     * 使用稳定系统编码执行并返回结果。
     *
     * @param systemActorCode 系统任务稳定编码，例如 {@code mom-wms-outbox-publisher}
     * @param action 当前线程内执行的动作
     * @param <T> 返回类型
     * @return 动作结果
     * @throws IllegalArgumentException 系统编码为空、格式不合法或含义过于模糊时抛出
     */
    public <T> T runAsSystem(String systemActorCode, Supplier<T> action) {
        String code = requireSystemActorCode(systemActorCode);
        AuditActor actor = new AuditActor(
                code,
                ActorType.SYSTEM,
                null,
                null,
                null,
                CorrelationContext.currentId());
        return runAsActor(actor, action);
    }

    /** 使用稳定系统编码执行无返回值动作。 */
    public void runAsSystem(String systemActorCode, Runnable action) {
        Objects.requireNonNull(action, "action 不能为空");
        runAsSystem(systemActorCode, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 使用调用方提供的不可变 Actor 执行动作，并在结束后恢复外层 Actor。
     */
    public <T> T runAsActor(AuditActor actor, Supplier<T> action) {
        AuditActor requiredActor = Objects.requireNonNull(actor, "actor 不能为空");
        Supplier<T> requiredAction = Objects.requireNonNull(action, "action 不能为空");
        AuditActor previous = AuditContext.bind(requiredActor);
        try {
            return requiredAction.get();
        } finally {
            AuditContext.restore(previous);
        }
    }

    /** 使用调用方提供的 Actor 执行无返回值动作。 */
    public void runAsActor(AuditActor actor, Runnable action) {
        Objects.requireNonNull(action, "action 不能为空");
        runAsActor(actor, () -> {
            action.run();
            return null;
        });
    }

    private static String requireSystemActorCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("systemActorCode 不能为空");
        }
        String normalized = value.trim();
        if (!SYSTEM_ACTOR_CODE.matcher(normalized).matches() || MEANINGLESS_CODES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "systemActorCode 必须是稳定、明确的小写短横线编码，不能使用 system/default/unknown");
        }
        return normalized;
    }
}
