package io.github.chrisshi.mom.idempotency;

/**
 * Redis 幂等保护不可用时的处理策略。
 */
public enum RedisFailureMode {

    /**
     * 关闭业务通路并抛出异常，优先保证不重复执行副作用。
     */
    FAIL_CLOSED,

    /**
     * 允许业务继续，但返回 BYPASSED 并要求调用方记录告警。
     */
    FAIL_OPEN
}
