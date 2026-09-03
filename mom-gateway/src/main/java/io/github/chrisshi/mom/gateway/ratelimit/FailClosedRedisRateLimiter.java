package io.github.chrisshi.mom.gateway.ratelimit;

import io.github.chrisshi.mom.gateway.error.GatewayErrorCode;
import io.github.chrisshi.mom.gateway.error.GatewayException;
import io.github.chrisshi.mom.metrics.MomMetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

/**
 * Gateway RedisRateLimiter 的 fail-closed 包装器。
 *
 * <p>保留 Spring Cloud Gateway 官方 Lua 与配置模型，只把官方 Redis 故障时的异常放行标记以及限流判定异常
 * 转换为统一 GatewayException，确保限流基础设施不可用时请求不会继续访问下游。</p>
 */
public final class FailClosedRedisRateLimiter implements RateLimiter<RedisRateLimiter.Config> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FailClosedRedisRateLimiter.class);
    private static final String OFFICIAL_FAILURE_REMAINING = "-1";

    private final RedisRateLimiter delegate;
    private final MeterRegistry meterRegistry;

    public FailClosedRedisRateLimiter(RedisRateLimiter delegate) {
        this(delegate, null);
    }

    public FailClosedRedisRateLimiter(RedisRateLimiter delegate, MeterRegistry meterRegistry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate 不能为空");
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        return delegate.isAllowed(routeId, id)
                .flatMap(response -> {
                    if (isOfficialFailureResponse(response)) {
                        recordOutcome(routeId, "unavailable");
                        return Mono.error(new GatewayException(GatewayErrorCode.RATE_LIMIT_UNAVAILABLE));
                    }
                    recordOutcome(routeId, response.isAllowed() ? "allowed" : "rejected");
                    return Mono.just(response);
                })
                .onErrorMap(exception -> {
                    if (exception instanceof GatewayException) {
                        return exception;
                    }
                    recordOutcome(routeId, "unavailable");
                    return new GatewayException(GatewayErrorCode.RATE_LIMIT_UNAVAILABLE, exception);
                });
    }

    @Override
    public Map<String, RedisRateLimiter.Config> getConfig() {
        return delegate.getConfig();
    }

    @Override
    public Class<RedisRateLimiter.Config> getConfigClass() {
        return delegate.getConfigClass();
    }

    @Override
    public RedisRateLimiter.Config newConfig() {
        return delegate.newConfig();
    }

    private boolean isOfficialFailureResponse(Response response) {
        String remaining = response.getHeaders().get(delegate.getRemainingHeader());
        return response.isAllowed() && OFFICIAL_FAILURE_REMAINING.equals(remaining);
    }

    private void recordOutcome(String routeId, String outcome) {
        if (meterRegistry == null) {
            return;
        }
        try {
            meterRegistry.counter(
                            MomMetricNames.GATEWAY_RATE_LIMIT_REQUESTS,
                            "route", normalizeRoute(routeId),
                            "outcome", outcome)
                    .increment();
        }
        catch (RuntimeException exception) {
            LOGGER.warn("Gateway 限流指标记录失败，限流结果保持不变。failureType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private static String normalizeRoute(String routeId) {
        return routeId == null || routeId.isBlank() ? "unknown-route" : routeId.trim();
    }
}
