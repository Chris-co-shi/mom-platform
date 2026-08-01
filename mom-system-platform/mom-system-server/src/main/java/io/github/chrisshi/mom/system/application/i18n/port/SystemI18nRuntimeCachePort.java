package io.github.chrisshi.mom.system.application.i18n.port;

import java.util.Optional;

/** Dynamic I18n 不可变发布 Snapshot 的 Redis Projection Cache Port。 */
public interface SystemI18nRuntimeCachePort {

    /** 读取与 PostgreSQL Header 完全匹配的版本化 Snapshot。 */
    Optional<SystemI18nRuntimeQueryPort.RuntimeSnapshot> find(
            SystemI18nRuntimeQueryPort.RuntimeHeader header);

    /** 写入与 PostgreSQL Header 完全匹配的版本化 Snapshot。 */
    void put(
            SystemI18nRuntimeQueryPort.RuntimeHeader header,
            SystemI18nRuntimeQueryPort.RuntimeSnapshot snapshot);

    /** 清理指定 Resource 的全部 Locale/版本 Projection。 */
    void evict(String applicationCode, String resourceCode);
}
