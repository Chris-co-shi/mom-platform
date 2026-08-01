package io.github.chrisshi.mom.resilience;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 防止 CircuitBreaker、Retry、Bulkhead 或 TimeLimiter 包装事务内部远程等待。
 *
 * <p>该 Guard 是 {@code Propagation.NEVER} 的显式运行时补充，用于 Feign Adapter 和其他 Resilience 入口。
 * 它只读取 Spring 当前线程事务状态，不开启、挂起或提交事务。检测到活动事务时 fail-fast，避免连接在远程
 * timeout 期间被占用。类无状态且线程安全。</p>
 */
public final class ResilienceTransactionGuard {

    private ResilienceTransactionGuard() {
    }

    /**
     * 断言当前线程没有活动数据库事务。
     *
     * @param operation 低敏操作说明，仅用于异常定位，不得包含 Token、URL 参数或业务 ID
     * @throws IllegalStateException 存在活动事务时抛出，远程调用不得继续
     */
    public static void requireNoActiveTransaction(String operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("禁止在活动数据库事务中执行 Resilience 远程调用: " + operation);
        }
    }
}
