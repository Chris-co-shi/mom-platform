package io.github.chrisshi.mom.cache.api;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 定义一个有类型、版本和失败边界的缓存区域。
 *
 * <p>Region 是业务模块消费 Framework 的配置契约，不是动态 Registry。业务拥有具体 Region 常量，Framework
 * 只负责按这里声明的 L1/L2 能力执行。Region 不携带租户来源或权限判断，Factory 隔离由
 * {@link CacheEntryKey} 的 {@link CacheScope} 明确表达。实例不可变且可安全跨线程共享。</p>
 *
 * @param boundedContext 拥有缓存数据的限界上下文
 * @param capability     缓存能力名称
 * @param keyVersion     物理 Key 版本，用于无扫描演进 L2 数据
 * @param valueType      精确缓存值类型
 * @param localTtl       L1 TTL
 * @param remoteTtl      L2 TTL
 * @param localEnabled   是否启用 L1
 * @param remoteEnabled  是否启用 L2
 * @param <T>            缓存值类型
 */
public record CacheRegion<T>(
        String boundedContext,
        String capability,
        int keyVersion,
        CacheValueType<T> valueType,
        Duration localTtl,
        Duration remoteTtl,
        boolean localEnabled,
        boolean remoteEnabled
) {

    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    public CacheRegion {
        requireSegment(boundedContext, "boundedContext");
        requireSegment(capability, "capability");
        Objects.requireNonNull(valueType, "缓存值类型不能为空");
        Objects.requireNonNull(localTtl, "L1 TTL 不能为空");
        Objects.requireNonNull(remoteTtl, "L2 TTL 不能为空");
        if (keyVersion <= 0) {
            throw new IllegalArgumentException("缓存 Key 版本必须为正数");
        }
        if (localTtl.isNegative() || localTtl.isZero() || remoteTtl.isNegative() || remoteTtl.isZero()) {
            throw new IllegalArgumentException("缓存 TTL 必须为正时长");
        }
        if (!localEnabled && !remoteEnabled) {
            throw new IllegalArgumentException("CacheRegion 至少启用一个缓存层");
        }
    }

    /**
     * 返回进程内稳定的 Region 身份，用于隔离 Caffeine 实例。
     *
     * @return 不包含 Subject 或敏感数据的低基数 Region 身份
     */
    public String identity() {
        return boundedContext + ":v" + keyVersion + ":" + capability;
    }

    private static void requireSegment(String value, String name) {
        Objects.requireNonNull(value, name + " 不能为空");
        if (!SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " 必须是可安全进入 Key 的小写名称");
        }
    }
}
