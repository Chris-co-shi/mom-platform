package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nException;
import io.github.chrisshi.mom.system.domain.i18n.SystemI18nRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dynamic I18n PostgreSQL Repository Adapter。
 *
 * <p>该 Adapter 只访问当前 {@code mom_system} search_path，使用参数化 SQL、affected rows CAS 和
 * {@code SELECT FOR UPDATE}。审计 Actor/UTC 时间由服务端注入；Release 只实现 INSERT 与 SELECT，数据库
 * 触发器进一步拒绝 UPDATE/DELETE。JSONB 读取时必须是字符串键值对象，否则 Fail Closed；数据库或 JSON
 * 损坏不会降级为 Draft、缓存或跨版本拼接。</p>
 */
@Repository
public class JdbcSystemI18nRepository implements SystemI18nRepository {
    private static final String RESOURCE_COLUMNS = """
            id, application_code, resource_code, resource_name, default_locale, enabled,
            published_version, published_by, published_at, version, description,
            created_by, created_at, updated_by, updated_at
            """;
    private static final String MESSAGE_COLUMNS = """
            id, resource_id, message_key, locale, message_value, enabled, version, description,
            created_by, created_at, updated_by, updated_at
            """;
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actorProvider;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public JdbcSystemI18nRepository(
            JdbcTemplate jdbc, CurrentActorProvider actorProvider, Clock clock, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.actorProvider = actorProvider;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /** 插入资源并返回服务端生成 ID 与审计后的行。 */
    @Override
    public Resource insertResource(Resource resource) {
        String id = IdWorker.getIdStr();
        String actor = actor();
        Instant now = clock.instant();
        try {
            jdbc.update("""
                    INSERT INTO system_i18n_resource (
                        id, application_code, resource_code, resource_name, default_locale, enabled,
                        published_version, published_by, published_at, version, description,
                        created_by, created_at, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, NULL, 0, ?, ?, ?, ?, ?)
                    """, id, resource.applicationCode(), resource.resourceCode(), resource.resourceName(),
                    resource.defaultLocale(), resource.enabled(), resource.description(), actor,
                    Timestamp.from(now), actor, Timestamp.from(now));
        } catch (DataIntegrityViolationException exception) {
            throw new SystemI18nException.Conflict("同一 applicationCode 内 resourceCode 已存在", exception);
        }
        return findResourceById(id).orElseThrow(() -> new IllegalStateException("资源插入后无法读取"));
    }

    /** 以 Version CAS 更新资源所有可变字段并增加版本。 */
    @Override
    public boolean updateResource(Resource resource) {
        String actor = actor();
        Instant now = clock.instant();
        return jdbc.update("""
                UPDATE system_i18n_resource
                   SET resource_name=?, enabled=?, published_version=?, published_by=?, published_at=?,
                       version=version+1, description=?, updated_by=?, updated_at=?
                 WHERE id=? AND version=?
                """, resource.resourceName(), resource.enabled(), resource.publishedVersion(),
                resource.publishedBy(), timestamp(resource.publishedAt()), resource.description(), actor,
                Timestamp.from(now), resource.id(), resource.version()) == 1;
    }

    @Override
    public Optional<Resource> findResourceById(String id) {
        return optionalQuery("SELECT " + RESOURCE_COLUMNS + " FROM system_i18n_resource WHERE id=?",
                RESOURCE_MAPPER, id);
    }

    @Override
    public Optional<Resource> findResourceByCodes(String applicationCode, String resourceCode) {
        return optionalQuery("SELECT " + RESOURCE_COLUMNS
                        + " FROM system_i18n_resource WHERE application_code=? AND resource_code=?",
                RESOURCE_MAPPER, applicationCode, resourceCode);
    }

    /** 使用 PostgreSQL 行锁串行化同一 Resource 的 Publish/Rollback。 */
    @Override
    public Optional<Resource> lockResource(String id) {
        return optionalQuery("SELECT " + RESOURCE_COLUMNS + " FROM system_i18n_resource WHERE id=? FOR UPDATE",
                RESOURCE_MAPPER, id);
    }

    @Override
    public Page<Resource> findResources(String applicationCode, Boolean enabled, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> arguments = new ArrayList<>();
        if (applicationCode != null) {
            where.append(" AND application_code=?");
            arguments.add(applicationCode);
        }
        if (enabled != null) {
            where.append(" AND enabled=?");
            arguments.add(enabled);
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM system_i18n_resource" + where,
                Long.class, arguments.toArray());
        arguments.add(size);
        arguments.add(Math.multiplyExact((long) page, size));
        List<Resource> items = jdbc.query("SELECT " + RESOURCE_COLUMNS + " FROM system_i18n_resource" + where
                        + " ORDER BY application_code, resource_code, id LIMIT ? OFFSET ?",
                RESOURCE_MAPPER, arguments.toArray());
        return new Page<>(items, total, page, size);
    }

    /** 插入 Draft 并将唯一/FK冲突映射为稳定 409。 */
    @Override
    public Message insertMessage(Message message) {
        String id = IdWorker.getIdStr();
        String actor = actor();
        Instant now = clock.instant();
        try {
            jdbc.update("""
                    INSERT INTO system_i18n_message (
                        id, resource_id, message_key, locale, message_value, enabled, version, description,
                        created_by, created_at, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
                    """, id, message.resourceId(), message.messageKey(), message.locale(), message.messageValue(),
                    message.enabled(), message.description(), actor, Timestamp.from(now), actor, Timestamp.from(now));
        } catch (DataIntegrityViolationException exception) {
            throw new SystemI18nException.Conflict("同一资源的 messageKey/locale 已存在", exception);
        }
        return findMessage(message.resourceId(), id)
                .orElseThrow(() -> new IllegalStateException("草稿插入后无法读取"));
    }

    /** 以 Version CAS 更新 Draft 文本、状态和说明。 */
    @Override
    public boolean updateMessage(Message message) {
        String actor = actor();
        Instant now = clock.instant();
        return jdbc.update("""
                UPDATE system_i18n_message
                   SET message_value=?, enabled=?, version=version+1, description=?, updated_by=?, updated_at=?
                 WHERE id=? AND resource_id=? AND version=?
                """, message.messageValue(), message.enabled(), message.description(), actor, Timestamp.from(now),
                message.id(), message.resourceId(), message.version()) == 1;
    }

    @Override
    public Optional<Message> findMessage(String resourceId, String messageId) {
        return optionalQuery("SELECT " + MESSAGE_COLUMNS
                        + " FROM system_i18n_message WHERE resource_id=? AND id=?",
                MESSAGE_MAPPER, resourceId, messageId);
    }

    @Override
    public List<Message> findEnabledMessages(String resourceId) {
        return jdbc.query("SELECT " + MESSAGE_COLUMNS
                        + " FROM system_i18n_message WHERE resource_id=? AND enabled=true"
                        + " ORDER BY message_key, locale, id",
                MESSAGE_MAPPER, resourceId);
    }

    @Override
    public Page<Message> findMessages(
            String resourceId, String messageKey, String locale, Boolean enabled, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE resource_id=?");
        List<Object> arguments = new ArrayList<>();
        arguments.add(resourceId);
        if (messageKey != null) {
            where.append(" AND message_key=?");
            arguments.add(messageKey);
        }
        if (locale != null) {
            where.append(" AND locale=?");
            arguments.add(locale);
        }
        if (enabled != null) {
            where.append(" AND enabled=?");
            arguments.add(enabled);
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM system_i18n_message" + where,
                Long.class, arguments.toArray());
        arguments.add(size);
        arguments.add(Math.multiplyExact((long) page, size));
        List<Message> items = jdbc.query("SELECT " + MESSAGE_COLUMNS + " FROM system_i18n_message" + where
                        + " ORDER BY message_key, locale, id LIMIT ? OFFSET ?",
                MESSAGE_MAPPER, arguments.toArray());
        return new Page<>(items, total, page, size);
    }

    /** 调用方已持有资源行锁，因此 max+1 对单资源并发安全。 */
    @Override
    public long nextReleaseVersion(String resourceId) {
        return jdbc.queryForObject("""
                SELECT coalesce(max(release_version), 0) + 1
                  FROM system_i18n_release
                 WHERE resource_id=?
                """, Long.class, resourceId);
    }

    /** 只追加单 Locale Release；同事务两次调用共同形成完整版本。 */
    @Override
    public void insertRelease(Release release) {
        try {
            jdbc.update("""
                    INSERT INTO system_i18n_release (
                        resource_id, release_version, locale, messages_json, message_count, fallback_count,
                        checksum, source_release_version, change_note, published_by, published_at)
                    VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?)
                    """, release.resourceId(), release.releaseVersion(), release.locale(), release.messagesJson(),
                    release.messageCount(), release.fallbackCount(), release.checksum(), release.sourceReleaseVersion(),
                    release.changeNote(), release.publishedBy(), Timestamp.from(release.publishedAt()));
        } catch (DataIntegrityViolationException exception) {
            throw new SystemI18nException.Conflict("发布版本写入冲突", exception);
        }
    }

    @Override
    public List<Release> findRelease(String resourceId, long releaseVersion) {
        return jdbc.query("""
                SELECT resource_id, release_version, locale, messages_json::text AS messages_json,
                       message_count, fallback_count, checksum, source_release_version, change_note,
                       published_by, published_at
                  FROM system_i18n_release
                 WHERE resource_id=? AND release_version=?
                 ORDER BY locale
                """, this::mapRelease, resourceId, releaseVersion);
    }

    @Override
    public Page<ReleaseSummary> findReleaseHistory(String resourceId, int page, int size) {
        long total = jdbc.queryForObject("""
                SELECT count(DISTINCT release_version) FROM system_i18n_release WHERE resource_id=?
                """, Long.class, resourceId);
        List<ReleaseSummary> items = jdbc.query("""
                SELECT release_version, max(source_release_version) AS source_release_version,
                       max(change_note) AS change_note, max(published_by) AS published_by,
                       max(published_at) AS published_at, count(*) AS locale_count
                  FROM system_i18n_release
                 WHERE resource_id=?
                 GROUP BY release_version
                 ORDER BY release_version DESC
                 LIMIT ? OFFSET ?
                """, (rs, row) -> new ReleaseSummary(rs.getLong("release_version"),
                nullableLong(rs, "source_release_version"), rs.getString("change_note"),
                rs.getString("published_by"), instant(rs, "published_at"), rs.getInt("locale_count")),
                resourceId, size, Math.multiplyExact((long) page, size));
        return new Page<>(items, total, page, size);
    }

    private Release mapRelease(ResultSet rs, int row) throws SQLException {
        String json = rs.getString("messages_json");
        return new Release(rs.getString("resource_id"), rs.getLong("release_version"), rs.getString("locale"),
                parseMessages(json), json, rs.getInt("message_count"), rs.getInt("fallback_count"),
                rs.getString("checksum"), nullableLong(rs, "source_release_version"), rs.getString("change_note"),
                rs.getString("published_by"), instant(rs, "published_at"));
    }

    private Map<String, String> parseMessages(String json) {
        try {
            Object value = objectMapper.readValue(json, Map.class);
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalStateException("Release messagesJson Root 不是 Object");
            }
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> {
                if (!(key instanceof String textKey) || !(item instanceof String textValue)) {
                    throw new IllegalStateException("Release messagesJson 必须是字符串键值 Object");
                }
                result.put(textKey, textValue);
            });
            return Map.copyOf(result);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Release messagesJson 损坏", exception);
        }
    }

    private String actor() {
        return actorProvider.requireCurrentActor().actorId();
    }

    private <T> Optional<T> optionalQuery(String sql, RowMapper<T> mapper, Object... arguments) {
        List<T> rows = jdbc.query(sql, mapper, arguments);
        return rows.stream().findFirst();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static final RowMapper<Resource> RESOURCE_MAPPER = (rs, row) -> new Resource(
            rs.getString("id"), rs.getString("application_code"), rs.getString("resource_code"),
            rs.getString("resource_name"), rs.getString("default_locale"), rs.getBoolean("enabled"),
            nullableLong(rs, "published_version"), rs.getString("published_by"), instant(rs, "published_at"),
            rs.getLong("version"), rs.getString("description"), rs.getString("created_by"),
            instant(rs, "created_at"), rs.getString("updated_by"), instant(rs, "updated_at"));

    private static final RowMapper<Message> MESSAGE_MAPPER = (rs, row) -> new Message(
            rs.getString("id"), rs.getString("resource_id"), rs.getString("message_key"),
            rs.getString("locale"), rs.getString("message_value"), rs.getBoolean("enabled"),
            rs.getLong("version"), rs.getString("description"), rs.getString("created_by"),
            instant(rs, "created_at"), rs.getString("updated_by"), instant(rs, "updated_at"));
}
