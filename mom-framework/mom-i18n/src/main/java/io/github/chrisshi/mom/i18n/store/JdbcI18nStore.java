package io.github.chrisshi.mom.i18n.store;

import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 使用宿主 DataSource 与本地事务的可选 JDBC I18n 持久化适配器。
 *
 * <p>表名仅由启动装配提供，构造时验证后固定；业务值全部使用绑定参数。复用宿主 ID、Actor 与时钟，
 * 不注册自动配置、不持有业务 Entity，也不创建事务。唯一冲突与其他数据库故障保留 Spring JDBC 分类，
 * 不重试、不跨 Owner 降级。逻辑删除仅过滤，SPI 不提供删除操作。</p>
 */
public final class JdbcI18nStore implements I18nStore {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]{0,62}");
    private static final String MESSAGE_COLUMNS = "id, message_key, description, enabled, version, updated_at";
    private static final String TRANSLATION_COLUMNS = "id, message_id, locale_code, message_text, version, updated_at";
    private static final RowMapper<I18nMessage> MESSAGE_ROW = (rs, row) -> new I18nMessage(
        rs.getString("id"), rs.getString("message_key"), rs.getString("description"),
        rs.getBoolean("enabled"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant());
    private static final RowMapper<I18nTranslation> TRANSLATION_ROW = (rs, row) -> new I18nTranslation(
        rs.getString("id"), rs.getString("message_id"), rs.getString("locale_code"),
        rs.getString("message_text"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant());

    private final NamedParameterJdbcTemplate jdbc;
    private final String messageTable;
    private final String translationTable;
    private final Supplier<String> ids;
    private final CurrentActorProvider actors;
    private final Clock clock;

    /**
     * 绑定当前 Owner 的两张表与现有技术能力；多数据源宿主必须传入与管理事务一致的数据源。
     *
     * @param dataSource       当前 Owner 数据源，Schema 由连接配置或显式表名前缀指定
     * @param messageTable     受信消息表名，允许一个小写 Schema 前缀，每段至多 63 个 ASCII 字符
     * @param translationTable 受信译文表名，格式同消息表名
     * @param ids              宿主既有 String ID 生成规则，不由适配器重新生成算法
     * @param actors           当前认证操作人；缺失时保留 null，由既有非空约束拒绝写入
     * @param clock            宿主 UTC Instant 时钟
     */
    public JdbcI18nStore(DataSource dataSource, String messageTable, String translationTable,
                         Supplier<String> ids, CurrentActorProvider actors, Clock clock) {
        this.messageTable = tableName(messageTable);
        this.translationTable = tableName(translationTable);
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
        this.ids = Objects.requireNonNull(ids, "ids");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public I18nMessage message(String id, boolean forUpdate) {
        return one(jdbc.query("SELECT " + MESSAGE_COLUMNS + " FROM " + messageTable
                + " WHERE id=:id AND deleted=false" + (forUpdate ? " FOR UPDATE" : ""),
            new MapSqlParameterSource("id", id), MESSAGE_ROW));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public I18nMessage message(String messageKey) {
        return one(jdbc.query("SELECT " + MESSAGE_COLUMNS + " FROM " + messageTable
                + " WHERE message_key=:key AND deleted=false",
            new MapSqlParameterSource("key", messageKey), MESSAGE_ROW));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<I18nMessage> messages() {
        return jdbc.query("SELECT " + MESSAGE_COLUMNS + " FROM " + messageTable
            + " WHERE deleted=false ORDER BY message_key, id", MESSAGE_ROW);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<I18nMessage> enabledMessages() {
        return jdbc.query("SELECT " + MESSAGE_COLUMNS + " FROM " + messageTable
            + " WHERE deleted=false AND enabled=true ORDER BY message_key, id", MESSAGE_ROW);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<I18nTranslation> translations(String messageId) {
        return jdbc.query("SELECT " + TRANSLATION_COLUMNS + " FROM " + translationTable
                + " WHERE message_id=:messageId AND deleted=false ORDER BY locale_code, id",
            new MapSqlParameterSource("messageId", messageId), TRANSLATION_ROW);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<I18nTranslation> translations(List<String> messageIds, List<String> localeCodes) {
        if (messageIds.isEmpty() || localeCodes.isEmpty()) return List.of();
        return jdbc.query("SELECT " + TRANSLATION_COLUMNS + " FROM " + translationTable
                + " WHERE deleted=false AND message_id IN (:ids) AND locale_code IN (:locales)",
            new MapSqlParameterSource("ids", messageIds).addValue("locales", localeCodes), TRANSLATION_ROW);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public I18nMessage createMessage(String messageKey, String description, boolean enabled) {
        String id = ids.get();
        Instant now = clock.instant();
        var parameters = audit(now).addValue("id", id).addValue("key", messageKey)
            .addValue("description", description).addValue("enabled", enabled);
        jdbc.update("INSERT INTO " + messageTable
            + " (id, message_key, description, enabled, version, deleted, created_by, created_at, updated_by, updated_at)"
            + " VALUES (:id, :key, :description, :enabled, 0, false, :actor, :now, :actor, :now)", parameters);
        return new I18nMessage(id, messageKey, description, enabled, 0, now);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> duplicateMessageKeys(String currentMessageId, String localeCode, String messageText) {
        return jdbc.queryForList("SELECT m.message_key FROM " + messageTable + " m"
            + " WHERE m.deleted=false AND m.id<>:currentId AND EXISTS (SELECT 1 FROM " + translationTable
            + " t WHERE t.message_id=m.id AND t.deleted=false AND t.locale_code=:locale AND t.message_text=:text)"
            + " ORDER BY m.message_key", new MapSqlParameterSource("currentId", currentMessageId)
            .addValue("locale", localeCode).addValue("text", messageText), String.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateMessage(String id, String description, boolean enabled, long version) {
        return jdbc.update("UPDATE " + messageTable
            + " SET description=:description, enabled=:enabled, version=version+1, updated_by=:actor, updated_at=:now"
            + " WHERE id=:id AND version=:version AND deleted=false", audit(clock.instant())
            .addValue("id", id).addValue("version", version).addValue("description", description)
            .addValue("enabled", enabled)) == 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public I18nTranslation createTranslation(String messageId, String localeCode, String messageText) {
        String id = ids.get();
        Instant now = clock.instant();
        jdbc.update("INSERT INTO " + translationTable
                + " (id, message_id, locale_code, message_text, version, deleted, created_by, created_at, updated_by, updated_at)"
                + " VALUES (:id, :messageId, :locale, :text, 0, false, :actor, :now, :actor, :now)",
            audit(now).addValue("id", id).addValue("messageId", messageId)
                .addValue("locale", localeCode).addValue("text", messageText));
        return new I18nTranslation(id, messageId, localeCode, messageText, 0, now);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateTranslation(String id, String messageText, long version) {
        return jdbc.update("UPDATE " + translationTable
            + " SET message_text=:text, version=version+1, updated_by=:actor, updated_at=:now"
            + " WHERE id=:id AND version=:version AND deleted=false", audit(clock.instant())
            .addValue("id", id).addValue("version", version).addValue("text", messageText)) == 1;
    }

    private MapSqlParameterSource audit(Instant now) {
        return new MapSqlParameterSource("now", Timestamp.from(now))
            .addValue("actor", actors.findCurrentActor().map(AuditActor::actorId).orElse(null));
    }

    private static <T> T one(List<T> rows) {
        return org.springframework.dao.support.DataAccessUtils.singleResult(rows);
    }

    private static String tableName(String name) {
        if (name == null) throw new IllegalArgumentException("表名不能为空");
        String[] parts = name.split("\\.", -1);
        if (parts.length > 2) throw new IllegalArgumentException("表名只允许一个 Schema 前缀");
        for (String part : parts) {
            if (!IDENTIFIER.matcher(part).matches())
                throw new IllegalArgumentException("表名必须是受信小写 SQL 标识符");
        }
        return "\"" + String.join("\".\"", parts) + "\"";
    }
}
