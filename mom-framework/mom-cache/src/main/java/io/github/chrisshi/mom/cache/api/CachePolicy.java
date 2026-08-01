package io.github.chrisshi.mom.cache.api;

import java.time.Duration;

/**
 * 旧 CacheType 对应的缓存策略。
 *
 * <p>该接口只为旧 API 兼容桥提供 TTL 与层级开关，不再承载新 Region。实现通常不可变且应可跨线程共享；
 * Registry 缺少策略时旧调用会明确失败，不会伪造默认业务语义。</p>
 *
 * @deprecated 新代码直接在 {@link CacheRegion} 声明 L1/L2 TTL 与启用状态
 */
@Deprecated(since = "P1.6", forRemoval = false)
public interface CachePolicy {

    Duration ttl();

    boolean localEnabled();

    boolean redisEnabled();
}
