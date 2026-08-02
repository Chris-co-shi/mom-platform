package io.github.chrisshi.mom.cache.api;

/**
 * P1.6 之前由 Framework 集中维护的缓存分类。
 *
 * <p>该枚举把 IAM/System 业务值放进 Framework，已由 {@link CacheRegion} 替代。为避免大爆炸迁移，本轮
 * 保留全部既有枚举值且禁止新增；只有真实消费者全部迁移、生产 Legacy 指标连续两个 Release 为零并接受
 * Removal ADR 后，才允许在后续 Major Cleanup 删除。</p>
 *
 * @deprecated 新代码应在所属 bounded context 定义类型化 {@link CacheRegion}
 */
@Deprecated(since = "P1.6", forRemoval = false)
public enum CacheType {

    IAM_PERMISSION,

    SYSTEM_DICTIONARY,

    SYSTEM_PARAMETER,

    SYSTEM_I18N,

    USER_SESSION
}
