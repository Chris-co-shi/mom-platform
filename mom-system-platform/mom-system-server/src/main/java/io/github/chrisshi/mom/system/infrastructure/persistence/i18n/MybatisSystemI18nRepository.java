package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nException;
import io.github.chrisshi.mom.system.domain.i18n.SystemI18nRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的 Dynamic I18n Repository Adapter。
 *
 * <p>Resource 与 Draft 的普通写入、单表条件查询、计数、固定排序和分页统一使用
 * {@code MomBaseMapper + LambdaQueryWrapper}，不为 MyBatis-Plus 已能清晰表达的查询重复创建 Mapper XML。
 * Resource 行锁通过服务端固定 {@code FOR UPDATE} 尾句表达；只有 JSONB 转换、版本聚合和历史投影继续保留
 * 在 Release 专用 Mapper XML。该 Adapter 不依赖 Spring JDBC 或 java.sql，也不创建第二套数据访问基础设施。</p>
 */
@Repository
public class MybatisSystemI18nRepository implements SystemI18nRepository {
    private final SystemI18nResourceMapper resourceMapper;
    private final SystemI18nMessageMapper messageMapper;
    private final SystemI18nReleaseMapper releaseMapper;
    private final ObjectMapper objectMapper;

    public MybatisSystemI18nRepository(
            SystemI18nResourceMapper resourceMapper,
            SystemI18nMessageMapper messageMapper,
            SystemI18nReleaseMapper releaseMapper,
            ObjectMapper objectMapper) {
        this.resourceMapper = resourceMapper;
        this.messageMapper = messageMapper;
        this.releaseMapper = releaseMapper;
        this.objectMapper = objectMapper;
    }

    /** 使用统一主键与审计填充插入 Resource，并重新读取数据库状态。 */
    @Override
    public Resource insertResource(Resource resource) {
        SystemI18nResourceEntity entity = toNewResourceEntity(resource);
        try {
            resourceMapper.insert(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new SystemI18nException.Conflict("同一 applicationCode 内 resourceCode 已存在", exception);
        }
        return findResourceById(entity.getId())
                .orElseThrow(() -> new IllegalStateException("资源插入后无法读取"));
    }

    /** 使用 MyBatis-Plus @Version 和统一更新审计修改 Resource。 */
    @Override
    public boolean updateResource(Resource resource) {
        SystemI18nResourceEntity entity = new SystemI18nResourceEntity();
        entity.setId(resource.id());
        entity.setVersion(resource.version());
        entity.setResourceName(resource.resourceName());
        entity.setEnabled(resource.enabled());
        entity.setPublishedVersion(resource.publishedVersion());
        entity.setPublishedBy(resource.publishedBy());
        entity.setPublishedAt(resource.publishedAt());
        entity.setDescription(resource.description());
        return resourceMapper.updateById(entity) == 1;
    }

    @Override
    public Optional<Resource> findResourceById(String id) {
        return Optional.ofNullable(resourceMapper.selectById(id))
                .map(MybatisSystemI18nRepository::toResource);
    }

    @Override
    public Optional<Resource> findResourceByCodes(String applicationCode, String resourceCode) {
        var query = Wrappers.<SystemI18nResourceEntity>lambdaQuery()
                .eq(SystemI18nResourceEntity::getApplicationCode, applicationCode)
                .eq(SystemI18nResourceEntity::getResourceCode, resourceCode);
        return Optional.ofNullable(resourceMapper.selectOne(query))
                .map(MybatisSystemI18nRepository::toResource);
    }

    @Override
    public Optional<Resource> lockResource(String id) {
        var query = Wrappers.<SystemI18nResourceEntity>lambdaQuery()
                .eq(SystemI18nResourceEntity::getId, id)
                .last("FOR UPDATE");
        return Optional.ofNullable(resourceMapper.selectOne(query))
                .map(MybatisSystemI18nRepository::toResource);
    }

    @Override
    public Page<Resource> findResources(String applicationCode, Boolean enabled, int page, int size) {
        long offset = Math.multiplyExact((long) page, size);
        long total = resourceMapper.selectCount(resourceFilter(applicationCode, enabled));
        List<Resource> items = total == 0 ? List.of() : resourceMapper.selectList(
                        resourceFilter(applicationCode, enabled)
                                .orderByAsc(SystemI18nResourceEntity::getApplicationCode)
                                .orderByAsc(SystemI18nResourceEntity::getResourceCode)
                                .orderByAsc(SystemI18nResourceEntity::getId)
                                .last(limitOffset(size, offset)))
                .stream()
                .map(MybatisSystemI18nRepository::toResource)
                .toList();
        return new Page<>(items, total, page, size);
    }

    /** 使用统一主键与审计填充插入 Draft，并重新读取父资源限定后的数据库状态。 */
    @Override
    public Message insertMessage(Message message) {
        SystemI18nMessageEntity entity = toNewMessageEntity(message);
        try {
            messageMapper.insert(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new SystemI18nException.Conflict("同一资源的 messageKey/locale 已存在", exception);
        }
        return findMessage(entity.getResourceId(), entity.getId())
                .orElseThrow(() -> new IllegalStateException("草稿插入后无法读取"));
    }

    /** 使用 MyBatis-Plus @Version 和统一更新审计修改 Draft。 */
    @Override
    public boolean updateMessage(Message message) {
        SystemI18nMessageEntity entity = new SystemI18nMessageEntity();
        entity.setId(message.id());
        entity.setVersion(message.version());
        entity.setMessageValue(message.messageValue());
        entity.setEnabled(message.enabled());
        entity.setDescription(message.description());
        return messageMapper.updateById(entity) == 1;
    }

    @Override
    public Optional<Message> findMessage(String resourceId, String messageId) {
        var query = Wrappers.<SystemI18nMessageEntity>lambdaQuery()
                .eq(SystemI18nMessageEntity::getResourceId, resourceId)
                .eq(SystemI18nMessageEntity::getId, messageId);
        return Optional.ofNullable(messageMapper.selectOne(query))
                .map(MybatisSystemI18nRepository::toMessage);
    }

    @Override
    public List<Message> findEnabledMessages(String resourceId) {
        return messageMapper.selectList(
                        Wrappers.<SystemI18nMessageEntity>lambdaQuery()
                                .eq(SystemI18nMessageEntity::getResourceId, resourceId)
                                .eq(SystemI18nMessageEntity::getEnabled, true)
                                .orderByAsc(SystemI18nMessageEntity::getMessageKey)
                                .orderByAsc(SystemI18nMessageEntity::getLocale)
                                .orderByAsc(SystemI18nMessageEntity::getId))
                .stream()
                .map(MybatisSystemI18nRepository::toMessage)
                .toList();
    }

    @Override
    public Page<Message> findMessages(
            String resourceId, String messageKey, String locale, Boolean enabled, int page, int size) {
        long offset = Math.multiplyExact((long) page, size);
        long total = messageMapper.selectCount(messageFilter(resourceId, messageKey, locale, enabled));
        List<Message> items = total == 0 ? List.of() : messageMapper.selectList(
                        messageFilter(resourceId, messageKey, locale, enabled)
                                .orderByAsc(SystemI18nMessageEntity::getMessageKey)
                                .orderByAsc(SystemI18nMessageEntity::getLocale)
                                .orderByAsc(SystemI18nMessageEntity::getId)
                                .last(limitOffset(size, offset)))
                .stream()
                .map(MybatisSystemI18nRepository::toMessage)
                .toList();
        return new Page<>(items, total, page, size);
    }

    @Override
    public long nextReleaseVersion(String resourceId) {
        return releaseMapper.selectNextVersion(resourceId);
    }

    /** 追加单 Locale Release，并将复合主键/FK冲突转换为稳定 409。 */
    @Override
    public void insertRelease(Release release) {
        try {
            if (releaseMapper.insertRelease(toReleaseEntity(release)) != 1) {
                throw new IllegalStateException("发布版本未插入预期的一行记录");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new SystemI18nException.Conflict("发布版本写入冲突", exception);
        }
    }

    @Override
    public List<Release> findRelease(String resourceId, long releaseVersion) {
        return releaseMapper.selectRelease(resourceId, releaseVersion).stream()
                .map(this::toRelease)
                .toList();
    }

    @Override
    public Page<ReleaseSummary> findReleaseHistory(String resourceId, int page, int size) {
        long offset = Math.multiplyExact((long) page, size);
        List<ReleaseSummary> items = releaseMapper.selectHistory(resourceId, size, offset).stream()
                .map(row -> new ReleaseSummary(row.getReleaseVersion(), row.getSourceReleaseVersion(),
                        row.getChangeNote(), row.getPublishedBy(), row.getPublishedAt(), row.getLocaleCount()))
                .toList();
        return new Page<>(items, releaseMapper.countHistory(resourceId), page, size);
    }

    private static LambdaQueryWrapper<SystemI18nResourceEntity> resourceFilter(
            String applicationCode, Boolean enabled) {
        return Wrappers.<SystemI18nResourceEntity>lambdaQuery()
                .eq(applicationCode != null, SystemI18nResourceEntity::getApplicationCode, applicationCode)
                .eq(enabled != null, SystemI18nResourceEntity::getEnabled, enabled);
    }

    private static LambdaQueryWrapper<SystemI18nMessageEntity> messageFilter(
            String resourceId, String messageKey, String locale, Boolean enabled) {
        return Wrappers.<SystemI18nMessageEntity>lambdaQuery()
                .eq(SystemI18nMessageEntity::getResourceId, resourceId)
                .eq(messageKey != null, SystemI18nMessageEntity::getMessageKey, messageKey)
                .eq(locale != null, SystemI18nMessageEntity::getLocale, locale)
                .eq(enabled != null, SystemI18nMessageEntity::getEnabled, enabled);
    }

    /**
     * 生成只包含服务端校验后非负数字的固定分页尾句。
     *
     * <p>该方法不接受客户端 SQL 标识符或表达式；page/size 已在 Application 层限制，因此不会形成动态 SQL 注入。</p>
     */
    private static String limitOffset(int size, long offset) {
        return "LIMIT " + size + " OFFSET " + offset;
    }

    private Release toRelease(SystemI18nReleaseEntity entity) {
        String json = entity.getMessagesJson();
        return new Release(entity.getResourceId(), entity.getReleaseVersion(), entity.getLocale(),
                parseMessages(json), json, entity.getMessageCount(), entity.getFallbackCount(), entity.getChecksum(),
                entity.getSourceReleaseVersion(), entity.getChangeNote(), entity.getPublishedBy(),
                entity.getPublishedAt());
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

    private static SystemI18nResourceEntity toNewResourceEntity(Resource resource) {
        SystemI18nResourceEntity entity = new SystemI18nResourceEntity();
        entity.setApplicationCode(resource.applicationCode());
        entity.setResourceCode(resource.resourceCode());
        entity.setResourceName(resource.resourceName());
        entity.setDefaultLocale(resource.defaultLocale());
        entity.setEnabled(resource.enabled());
        entity.setDescription(resource.description());
        return entity;
    }

    private static SystemI18nMessageEntity toNewMessageEntity(Message message) {
        SystemI18nMessageEntity entity = new SystemI18nMessageEntity();
        entity.setResourceId(message.resourceId());
        entity.setMessageKey(message.messageKey());
        entity.setLocale(message.locale());
        entity.setMessageValue(message.messageValue());
        entity.setEnabled(message.enabled());
        entity.setDescription(message.description());
        return entity;
    }

    private static SystemI18nReleaseEntity toReleaseEntity(Release release) {
        SystemI18nReleaseEntity entity = new SystemI18nReleaseEntity();
        entity.setResourceId(release.resourceId());
        entity.setReleaseVersion(release.releaseVersion());
        entity.setLocale(release.locale());
        entity.setMessagesJson(release.messagesJson());
        entity.setMessageCount(release.messageCount());
        entity.setFallbackCount(release.fallbackCount());
        entity.setChecksum(release.checksum());
        entity.setSourceReleaseVersion(release.sourceReleaseVersion());
        entity.setChangeNote(release.changeNote());
        entity.setPublishedBy(release.publishedBy());
        entity.setPublishedAt(release.publishedAt());
        return entity;
    }

    private static Resource toResource(SystemI18nResourceEntity entity) {
        return new Resource(entity.getId(), entity.getApplicationCode(), entity.getResourceCode(),
                entity.getResourceName(), entity.getDefaultLocale(), Boolean.TRUE.equals(entity.getEnabled()),
                entity.getPublishedVersion(), entity.getPublishedBy(), entity.getPublishedAt(), entity.getVersion(),
                entity.getDescription(), entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedBy(),
                entity.getUpdatedAt());
    }

    private static Message toMessage(SystemI18nMessageEntity entity) {
        return new Message(entity.getId(), entity.getResourceId(), entity.getMessageKey(), entity.getLocale(),
                entity.getMessageValue(), Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(),
                entity.getDescription(), entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedBy(),
                entity.getUpdatedAt());
    }
}
