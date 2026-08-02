package io.github.chrisshi.mom.resilience;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * 验证 CircuitBreaker/Retry/TimeLimiter 等远程执行入口不能在活动数据库事务中运行。
 */
class ResilienceTransactionGuardTest {

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void shouldAllowExecutionWithoutTransaction() {
        assertThatCode(() -> ResilienceTransactionGuard.requireNoActiveTransaction("iam query"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectExecutionInsideTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatIllegalStateException()
                .isThrownBy(() -> ResilienceTransactionGuard.requireNoActiveTransaction("iam query"))
                .withMessageContaining("iam query");
    }
}
