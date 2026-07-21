package io.github.chrisshi.mom.ratelimit;

import io.github.chrisshi.mom.metrics.MomMetricNames;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link FailClosedRedisRateLimiter} 的契约与指标测试。
 *
 * <p>测试不复制官方 Redis Lua 实现，只模拟官方 {@link RedisRateLimiter} 的公开响应，验证正常限流结果
 * 原样透传、Redis 异常时官方返回的“允许且剩余令牌为 -1”会被转换为 MOM fail-closed 异常，并且指标只
 * 使用路由和结果标签。</p>
 */
class FailClosedRedisRateLimiterTest {

    /**
     * 验证正常官方响应不会被包装器修改，并记录 allowed 指标。
     */
    @Test
    void shouldKeepNormalOfficialResponseAndRecordAllowedMetric() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimiter.Response officialResponse = new RateLimiter.Response(
                true,
                Map.of(RedisRateLimiter.REMAINING_HEADER, "8"));
        when(delegate.getRemainingHeader()).thenReturn(RedisRateLimiter.REMAINING_HEADER);
        when(delegate.isAllowed("integration-service", "ip:127.0.0.1"))
                .thenReturn(Mono.just(officialResponse));

        FailClosedRedisRateLimiter limiter = new FailClosedRedisRateLimiter(delegate, registry);
        RateLimiter.Response actual = limiter.isAllowed(
                        "integration-service",
                        "ip:127.0.0.1")
                .block();

        assertSame(officialResponse, actual);
        assertEquals("8", actual.getHeaders().get(RedisRateLimiter.REMAINING_HEADER));
        assertEquals(1.0, registry.get(MomMetricNames.GATEWAY_RATE_LIMIT_REQUESTS)
                .tags("route", "integration-service", "outcome", "allowed")
                .counter()
                .count());
    }

    /**
     * 验证官方拒绝结果保持不变，并记录 rejected 指标。
     */
    @Test
    void shouldRecordRejectedMetricWithoutChangingOfficialResponse() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimiter.Response officialResponse = new RateLimiter.Response(
                false,
                Map.of(RedisRateLimiter.REMAINING_HEADER, "0"));
        when(delegate.getRemainingHeader()).thenReturn(RedisRateLimiter.REMAINING_HEADER);
        when(delegate.isAllowed("integration-service", "ip:127.0.0.1"))
                .thenReturn(Mono.just(officialResponse));

        RateLimiter.Response actual = new FailClosedRedisRateLimiter(delegate, registry)
                .isAllowed("integration-service", "ip:127.0.0.1")
                .block();

        assertFalse(actual.isAllowed());
        assertEquals(1.0, registry.get(MomMetricNames.GATEWAY_RATE_LIMIT_REQUESTS)
                .tags("route", "integration-service", "outcome", "rejected")
                .counter()
                .count());
    }

    /**
     * 验证官方异常放行标记被转换为 fail-closed 异常，并记录 unavailable 指标。
     */
    @Test
    void shouldRejectOfficialRedisFailureResponseAndRecordUnavailableMetric() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimiter.Response officialFailureResponse = new RateLimiter.Response(
                true,
                Map.of(RedisRateLimiter.REMAINING_HEADER, "-1"));
        when(delegate.getRemainingHeader()).thenReturn(RedisRateLimiter.REMAINING_HEADER);
        when(delegate.isAllowed("integration-service", "ip:127.0.0.1"))
                .thenReturn(Mono.just(officialFailureResponse));

        FailClosedRedisRateLimiter limiter = new FailClosedRedisRateLimiter(delegate, registry);

        assertThrows(RedisRateLimitUnavailableException.class, () -> limiter.isAllowed(
                        "integration-service",
                        "ip:127.0.0.1")
                .block());
        assertEquals(1.0, registry.get(MomMetricNames.GATEWAY_RATE_LIMIT_REQUESTS)
                .tags("route", "integration-service", "outcome", "unavailable")
                .counter()
                .count());
    }

    /**
     * 指标注册表缺失时限流器仍必须保持原有协议行为。
     */
    @Test
    void shouldOperateWithoutMeterRegistry() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        RateLimiter.Response officialResponse = new RateLimiter.Response(
                true,
                Map.of(RedisRateLimiter.REMAINING_HEADER, "5"));
        when(delegate.getRemainingHeader()).thenReturn(RedisRateLimiter.REMAINING_HEADER);
        when(delegate.isAllowed("integration-service", "ip:127.0.0.1"))
                .thenReturn(Mono.just(officialResponse));

        RateLimiter.Response actual = new FailClosedRedisRateLimiter(delegate)
                .isAllowed("integration-service", "ip:127.0.0.1")
                .block();

        assertSame(officialResponse, actual);
    }
}
