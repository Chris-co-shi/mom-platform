package io.github.chrisshi.mom.cache.core;

import io.github.chrisshi.mom.cache.api.CacheLayer;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 统一记录 MOM Cache 的低基数 Micrometer 指标。
 *
 * <p>指标只使用固定 Layer/Operation 标签，不包含 Key、Subject、Factory、用户或业务单号。MeterRegistry
 * 缺失时退化为无操作，遥测故障不改变缓存失败语义；应用启用 Prometheus Registry 后会自动导出相同
 * Meter 名称。类无业务状态，可跨线程复用。</p>
 */
public final class CacheMetrics {

    /** Cache 命中总数。 */
    public static final String HIT = "mom.cache.hit";
    /** Cache Miss 总数。 */
    public static final String MISS = "mom.cache.miss";
    /** Cache 精确或 Region 失效总数。 */
    public static final String EVICTION = "mom.cache.eviction";
    /** Cache 基础设施或格式错误总数。 */
    public static final String ERROR = "mom.cache.error";
    /** Redis 超时总数。 */
    public static final String REDIS_TIMEOUT = "mom.cache.redis.timeout";
    /** 旧 Cache API 调用总数。 */
    public static final String LEGACY_USAGE = "mom.cache.legacy.usage";

    private final MeterRegistry meterRegistry;

    /**
     * 创建指标记录器。
     *
     * @param meterRegistry 可选 Registry；为 null 时关闭指标但不影响缓存
     */
    public CacheMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** @param layer 命中的缓存层 */
    public void hit(CacheLayer layer) {
        increment(HIT, "layer", layer.name().toLowerCase());
    }

    /** @param layer 发生 Miss 的缓存层 */
    public void miss(CacheLayer layer) {
        increment(MISS, "layer", layer.name().toLowerCase());
    }

    /** @param operation 固定失效类型：key 或 region */
    public void eviction(String operation) {
        increment(EVICTION, "operation", operation);
    }

    /** @param operation 固定错误阶段，不得传入异常消息或 Key */
    public void error(String operation) {
        increment(ERROR, "operation", operation);
    }

    /** 记录一次 Redis timeout，不抛出异常。 */
    public void redisTimeout() {
        increment(REDIS_TIMEOUT);
    }

    /** 记录一次旧 API 调用，作为两个 Release 退出门禁的数据源。 */
    public void legacyUsage() {
        increment(LEGACY_USAGE);
    }

    private void increment(String name, String... tags) {
        if (meterRegistry != null) {
            meterRegistry.counter(name, tags).increment();
        }
    }
}
