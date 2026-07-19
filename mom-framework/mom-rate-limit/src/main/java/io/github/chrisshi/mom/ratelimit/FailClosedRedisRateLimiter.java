package io.github.chrisshi.mom.ratelimit;

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
 * 将 Spring Cloud Gateway 官方 Redis 令牌桶转换为 fail-closed 语义的包装器。
 *
 * <p>官方 {@link RedisRateLimiter} 在 Redis Lua 命令异常时会记录错误并返回允许结果，同时把
 * {@code X-RateLimit-Remaining} 设置为 {@code -1}。这一默认行为适合“限流系统不能成为硬依赖”的
 * 通用网关，但不符合 MOM 当前对核心服务的保护要求。本包装器保留官方 Lua、路由配置和令牌桶算法，
 * 只识别官方异常标记并转换为 {@link RedisRateLimitUnavailableException}。</p>
 *
 * <p>每次判定记录 {@code route + outcome} 两个低基数标签，结果限定为 {@code allowed}、
 * {@code rejected}、{@code unavailable}。限流身份、用户、IP 和 Token 不进入指标标签。指标注册异常只记录
 * 警告，不得改变官方判定结果或 fail-closed 语义。</p>
 *
 * <p>该实现没有复制或修改 Spring Cloud Gateway 源码，而是通过公开 {@link RateLimiter} 契约组合
 * 官方实现。路由配置仍由官方 {@link RedisRateLimiter.Config} 解析，避免形成两套配置模型。</p>
 */
public final class FailClosedRedisRateLimiter implements RateLimiter<RedisRateLimiter.Config> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FailClosedRedisRateLimiter.class);
    private static final String OFFICIAL_FAILURE_REMAINING = "-1";

    private final RedisRateLimiter delegate;
    private final MeterRegistry meterRegistry;

    /**
     * 创建 fail-closed Redis 限流包装器。
     *
     * @param delegate Spring Cloud Gateway 自动配置创建的官方 RedisRateLimiter
     * @param meterRegistry Spring Boot 管理的 Micrometer 注册表
     */
    public FailClosedRedisRateLimiter(RedisRateLimiter delegate, MeterRegistry meterRegistry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate 不能为空");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry 不能为空");
    }

    /**
     * 调用官方令牌桶，把异常放行标记转换为错误信号，并记录低基数结果指标。
     *
     * @param routeId 当前 Gateway 路由标识
     * @param id KeyResolver 解析出的限流身份；不会进入指标标签
     * @return 正常时返回官方限流结果；Redis 不可用时返回错误信号
     */
    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        return delegate.isAllowed(routeId, id)
                .flatMap(response -> {
                    if (isOfficialFailureResponse(response)) {
                        recordOutcome(routeId, "unavailable");
                        return Mono.error(new RedisRateLimitUnavailableException(
                                "Redis 令牌桶不可用，已按 fail-closed 阻止请求继续访问下游"));
                    }
                    recordOutcome(routeId, response.isAllowed() ? "allowed" : "rejected");
                    return Mono.just(response);
                })
                .doOnError(exception -> {
                    if (!(exception instanceof RedisRateLimitUnavailableException)) {
                        recordOutcome(routeId, "unavailable");
                    }
                });
    }

    /**
     * 复用官方路由级限流配置映射。
     *
     * @return 按路由标识保存的 RedisRateLimiter 配置
     */
    @Override
    public Map<String, RedisRateLimiter.Config> getConfig() {
        return delegate.getConfig();
    }

    /**
     * 复用官方配置类型，确保 YAML 参数仍由 Spring Cloud Gateway 绑定。
     *
     * @return RedisRateLimiter.Config 类型
     */
    @Override
    public Class<RedisRateLimiter.Config> getConfigClass() {
        return delegate.getConfigClass();
    }

    /**
     * 创建新的官方路由配置对象。
     *
     * @return 空的 RedisRateLimiter.Config
     */
    @Override
    public RedisRateLimiter.Config newConfig() {
        return delegate.newConfig();
    }

    /**
     * 判断官方 RedisRateLimiter 是否因 Redis 异常而执行了默认放行。
     */
    private boolean isOfficialFailureResponse(Response response) {
        String remaining = response.getHeaders().get(delegate.getRemainingHeader());
        return response.isAllowed() && OFFICIAL_FAILURE_REMAINING.equals(remaining);
    }

    /**
     * 记录稳定路由和结果，不允许可观测性异常改变限流结果。
     */
    private void recordOutcome(String routeId, String outcome) {
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
