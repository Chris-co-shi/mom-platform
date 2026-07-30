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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 基于 MyBatis-Plus 的 Dynamic I18n Repository Adapter。
 *
 * <p>Resource、Message 与 Release 全部使用 BaseEntity、MomBaseMapper 和 Wrapper。JSONB 通过字段级
 * TypeHandler 映射；版本聚合、Distinct 历史版本与固定分页通过 QueryWrapper 的受控服务端表达式完成。
 * System Dynamic I18n 不维护 Mapper XML，也不依赖 Spring JDBC 或 java.sql。</p>
 */
@Repository
public class MybatisSystemI18nRepository implements SystemI18nRepository {
    private static final String RELEASE_VERSION_COLUMN = "release_version";
    private static final String RESOURCE_ID_COLUMN = "resource_id";

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
        var query = Wrappers.<SystemI18nReleaseEntity>query()
                .select("COALESCE(MAX(" + RELEASE_VERSION_COLUMN + "), 0) + 1")
                .eq(RESOURCE_ID_COLUMN, resourceId);
        return singleLong(releaseMapper.selectObjs(query), "无法分配下一发布版本");
    }

    @Override
    public void insertRelease(Release release) {
        try {
            if (releaseMapper.insert(toReleaseEntity(release)) != 1) {
                throw new IllegalStateException("发布版本未插入预期的一行记录");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new SystemI18nException.Conflict("发布版本写入冲突", exception);
        }
    }

    @Override
    public List<Release> findRelease(String resourceId, long releaseVersion) {
        return releaseMapper.selectList(
                        Wrappers.<SystemI18nReleaseEntity>lambdaQuery()
                                .eq(SystemI18nReleaseEntity::getResourceId, resourceId)
                                .eq(SystemI18nReleaseEntity::getReleaseVersion, releaseVersion)
                                .orderByAsc(SystemI18nReleaseEntity::getLocale))
                .stream()
                .map(this::toRelease)
                .toList();
    }

    @Override
    public Page<ReleaseSummary> findReleaseHistory(String resourceId, int page, int size) {
        long offset = Math.multiplyExact((long) page, size);
        var countQuery = Wrappers.<SystemI18nReleaseEntity>query()
                .select("COUNT(DISTINCT " + RELEASE_VERSION_COLUMN + ")")
                .eq(RESOURCE_ID_COLUMN, resourceId);
        long total = singleLong(releaseMapper.selectObjs(countQuery), "无法统计发布历史");
        if (total == 0) {
            return new Page<>(List.of(), 0, page, size);
        }

        var versionQuery = Wrappers.<SystemI18nReleaseEntity>query()
                .select("DISTINCT " + RELEASE_VERSION_COLUMN)
                .eq(RESOURCE_ID_COLUMN, resourceId)
                .orderByDesc(RELEASE_VERSION_COLUMN)
                .last(limitOffset(size, offset));
        List<Long> versions = releaseMapper.selectObjs(versionQuery).stream()
                .map(value -> number(value, "发布历史版本类型无效").longValue())
                .toList();
        if (versions.isEmpty()) {
            return new Page<>(List.of(), total, page, size);
        }

        List<SystemI18nReleaseEntity> rows = releaseMapper.selectList(
                Wrappers.<SystemI18nReleaseEntity>lambdaQuery()
                        .eq(SystemI18nReleaseEntity::getResourceId, resourceId)
                        .in(SystemI18nReleaseEntity::getReleaseVersion, versions)
                        .orderByDesc(SystemI18nReleaseEntity::getReleaseVersion)
                        .orderByAsc(SystemI18nReleaseEntity::getLocale));
        Map<Long, List<SystemI18nReleaseEntity>> rowsByVersion = rows.stream()
                .collect(Collectors.groupingBy(
                        SystemI18nReleaseEntity::getReleaseVersion,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<ReleaseSummary> items = versions.stream()
                .map(version -> toReleaseSummary(rowsByVersion.get(version)))
                .toList();
        return new Page<>(items, total, page, size);
    }

    private static ReleaseSummary toReleaseSummary(List<SystemI18nReleaseEntity> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("发布历史版本缺少 Locale 快照");
        }
        SystemI18nReleaseEntity first = rows.getFirst();
        boolean consistent = rows.stream().allMatch(row ->
                Objects.equals(row.getSourceReleaseVersion(), first.getSourceReleaseVersion())
                        && Objects.equals(row.getChangeNote(), first.getChangeNote())
                        && Objects.equals(row.getPublishedBy(), first.getPublishedBy())
                        && Objects.equals(row.getPublishedAt(), first.getPublishedAt()));
        if (!consistent) {
            throw new IllegalStateException("同一发布版本的审计元数据不一致");
        }
        return new ReleaseSummary(first.getReleaseVersion(), first.getSourceReleaseVersion(),
                first.getChangeNote(), first.getPublishedBy(), first.getPublishedAt(), rows.size());
    }

    private static long singleLong(List<Object> values, String message) {
        if (values.size() != 1) {
            throw new IllegalStateException(message);
        }
        return number(values.getFirst(), message).longValue();
    }

    private static Number number(Object value, String message) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException(message);
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
