package io.github.chrisshi.mom.system.application.i18n.port;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Dynamic I18n Runtime 的轻量 PostgreSQL 查询 Port。
 *
 * <p>Header 查询不得读取 messages_json；调用方先以 Header 验证 Resource Kill Switch、当前发布版本、Locale
 * 完整性与 checksum，再访问 Redis。Cache Miss 时才通过 {@link #findSnapshot(RuntimeHeader)} 读取单 Locale
 * 完整 JSONB。数据库异常直接向上传播，不使用 Cache 伪造权威状态。</p>
 */
public interface SystemI18nRuntimeQueryPort {

    /** 查询已启用、已发布且双 Locale 完整的单 Locale Runtime Header。 */
    Optional<RuntimeHeader> findHeader(
            String applicationCode,
            String resourceCode,
            String locale);

    /** 按已确认 Header 读取单 Locale 完整不可变 Snapshot。 */
    Optional<RuntimeSnapshot> findSnapshot(RuntimeHeader header);

    record RuntimeHeader(
            String resourceId,
            String applicationCode,
            String resourceCode,
            String defaultLocale,
            long releaseVersion,
            String locale,
            String checksum,
            int fallbackCount,
            Instant publishedAt) {
    }

    record RuntimeSnapshot(
            String applicationCode,
            String resourceCode,
            String locale,
            String defaultLocale,
            long releaseVersion,
            String checksum,
            int fallbackCount,
            Instant publishedAt,
            Map<String, String> messages) {
        public RuntimeSnapshot {
            messages = messages == null ? Map.of() : Map.copyOf(messages);
        }
    }
}
