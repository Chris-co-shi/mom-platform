package io.github.chrisshi.mom.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link FailClosedRedisRateLimiter} 的契约测试。
 *
 * <p>测试不复制官方 Redis Lua 实现，只模拟官方 {@link RedisRateLimiter} 的公开响应，验证正常限流结果
 * 原样透传，以及 Redis 异常时官方返回的“允许且剩余令牌为 -1”会被转换为 MOM fail-closed 异常。</p>
 */
class FailClosedRedisRateLimiterTest {

    /**
     * 验证正常官方响应不会被包装器修改。
     */
    @Test
    void shouldKeepNormalOfficialResponse() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RateLimiter.Response officialResponse = new RateLimiter.Response(
                true,
                Map.of(RedisRateLimiter.REMAINING_HEADER, "8"));
        when(delegate.getRemainingHeader()).thenReturn(RedisRateLimiter.REMAINING_HEADER);
        when(delegate.isAllowed("integration-service", "ip:127.0.0.1"))
                .thenReturn(Mono.just(officialResponse));

        FailClosedRedisRateLimiter limiter = new FailClosedRedisRateLimiter(delegate);
        RateLimiter.Response actual = limiter.isAllowed(
                        "integration-service",
                        "ip:127.0.0.1")
                .block();

        assertSame(officialResponse, actual);
        assertEquals("8", actual.getHeaders().get(RedisRateLimiter.REMAINING_HEADER));
    }

    /**
     * 验证官方异常放行标记被转换为 fail-closed 异常，不能继续访问下游。
     */
    @Test
    void shouldRejectOfficialRedisFailureResponse() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RateLimiter.Response officialFailureResponse = new RateLimiter.Response(
                true,
                Map.of(RedisRateLimiter.REMAINING_HEADER, "-1"));
        when(delegate.getRemainingHeader()).thenReturn(RedisRateLimiter.REMAINING_HEADER);
        when(delegate.isAllowed("integration-service", "ip:127.0.0.1"))
                .thenReturn(Mono.just(officialFailureResponse));

        FailClosedRedisRateLimiter limiter = new FailClosedRedisRateLimiter(delegate);

        assertThrows(RedisRateLimitUnavailableException.class, () -> limiter.isAllowed(
                        "integration-service",
                        "ip:127.0.0.1")
                .block());
    }
}
