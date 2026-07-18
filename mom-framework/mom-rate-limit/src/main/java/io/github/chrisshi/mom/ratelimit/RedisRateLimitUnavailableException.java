package io.github.chrisshi.mom.ratelimit;

/**
 * Gateway Redis 令牌桶不可用异常。
 *
 * <p>Spring Cloud Gateway 官方 {@code RedisRateLimiter} 在 Redis 命令失败时默认返回“允许请求”并把
 * 剩余令牌 Header 设为 {@code -1}。MOM 的 fail-closed 包装器识别该标记后抛出本异常，再由统一
 * WebExceptionHandler 映射为 HTTP 503，防止 Redis 故障期间流量绕过共享限流。</p>
 */
public final class RedisRateLimitUnavailableException extends RuntimeException {

    /**
     * 创建限流基础设施不可用异常。
     *
     * @param message 面向日志与错误响应映射的描述
     */
    public RedisRateLimitUnavailableException(String message) {
        super(message);
    }
}
