package io.github.chrisshi.mom.core.context;

import java.util.UUID;

/**
 * Request-scoped correlation identifier for synchronous service calls.
 *
 * <p>The servlet filter owns the lifecycle and must clear the value after the request.</p>
 */
public final class CorrelationContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationContext() {
    }

    public static String resolveOrGenerate(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return candidate.trim();
    }

    public static void set(String correlationId) {
        CURRENT.set(resolveOrGenerate(correlationId));
    }

    public static String currentId() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
