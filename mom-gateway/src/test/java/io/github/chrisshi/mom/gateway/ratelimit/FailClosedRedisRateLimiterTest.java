package io.github.chrisshi.mom.gateway.ratelimit;

import io.github.chrisshi.mom.gateway.error.GatewayErrorCode;
import io.github.chrisshi.mom.gateway.error.GatewayException;
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

class FailClosedRedisRateLimiterTest {

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
        RateLimiter.Response actual = limiter.isAllowed("integration-service", "ip:127.0.0.1").block();

        assertSame(officialResponse, actual);
        assertEquals("8", actual.getHeaders().get(RedisRateLimiter.REMAINING_HEADER));
        assertEquals(1.0, registry.get(MomMetricNames.GATEWAY_RATE_LIMIT_REQUESTS)
                .tags("route", "integration-service", "outcome", "allowed")
                .counter()
                .count());
    }

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

    @Test
    void shouldConvertOfficialRedisFailureResponseToGatewayException() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimiter.Response officialFailureResponse = new RateLimiter.Response(
                true,
                Map.of(RedisRateLimiter.REMAINING_HEADER, "-1"));
        when(delegate.getRemainingHeader()).thenReturn(RedisRateLimiter.REMAINING_HEADER);
        when(delegate.isAllowed("integration-service", "ip:127.0.0.1"))
                .thenReturn(Mono.just(officialFailureResponse));

        FailClosedRedisRateLimiter limiter = new FailClosedRedisRateLimiter(delegate, registry);

        GatewayException exception = assertThrows(GatewayException.class, () -> limiter
                .isAllowed("integration-service", "ip:127.0.0.1")
                .block());
        assertEquals(GatewayErrorCode.RATE_LIMIT_UNAVAILABLE, exception.errorCode());
        assertEquals(1.0, registry.get(MomMetricNames.GATEWAY_RATE_LIMIT_REQUESTS)
                .tags("route", "integration-service", "outcome", "unavailable")
                .counter()
                .count());
    }

    @Test
    void shouldConvertDelegateFailureToGatewayException() {
        RedisRateLimiter delegate = mock(RedisRateLimiter.class);
        when(delegate.isAllowed("integration-service", "ip:127.0.0.1"))
                .thenReturn(Mono.error(new IllegalStateException("redis failure")));

        GatewayException exception = assertThrows(GatewayException.class, () -> new FailClosedRedisRateLimiter(delegate)
                .isAllowed("integration-service", "ip:127.0.0.1")
                .block());

        assertEquals(GatewayErrorCode.RATE_LIMIT_UNAVAILABLE, exception.errorCode());
    }

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
