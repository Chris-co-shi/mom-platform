package io.github.chrisshi.mom.system.infrastructure.persistence.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.system.application.catalog.CatalogI18nReferenceQuery;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemI18nReleaseEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemI18nResourceEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.mapper.SystemI18nReleaseMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.mapper.SystemI18nResourceMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dynamic I18n 当前完整发布版本的批量 MyBatis-Plus Query Adapter。
 *
 * <p>固定两阶段查询：先批量查 Resource，再按每 100 个 Resource 批量查其当前 Release；不按导航节点查询、
 * 不跨 Schema、不使用 XML/JDBC。双 Locale 任一缺失或 JSON 损坏时对应 Reference 不返回。</p>
 */
@Repository
public class MybatisCatalogI18nReferenceQuery implements CatalogI18nReferenceQuery {
    private static final int RELEASE_QUERY_BATCH = 100;
    private final SystemI18nResourceMapper resourceMapper;
    private final SystemI18nReleaseMapper releaseMapper;
    private final ObjectMapper objectMapper;

    public MybatisCatalogI18nReferenceQuery(
            SystemI18nResourceMapper resourceMapper,
            SystemI18nReleaseMapper releaseMapper,
            ObjectMapper objectMapper) {
        this.resourceMapper = resourceMapper;
        this.releaseMapper = releaseMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Set<Reference> findPublished(String applicationCode, Set<Reference> references) {
        if (references == null || references.isEmpty()) {
            return Set.of();
        }
        Set<String> resourceCodes = references.stream().map(Reference::resourceCode)
                .collect(java.util.stream.Collectors.toSet());
        List<SystemI18nResourceEntity> resources = resourceMapper.selectList(
                Wrappers.<SystemI18nResourceEntity>lambdaQuery()
                        .eq(SystemI18nResourceEntity::getApplicationCode, applicationCode)
                        .in(SystemI18nResourceEntity::getResourceCode, resourceCodes)
                        .eq(SystemI18nResourceEntity::getEnabled, true)
                        .isNotNull(SystemI18nResourceEntity::getPublishedVersion));
        if (resources.isEmpty()) {
            return Set.of();
        }

        List<SystemI18nReleaseEntity> releaseRows = new ArrayList<>();
        for (int startIndex = 0; startIndex < resources.size(); startIndex += RELEASE_QUERY_BATCH) {
            List<SystemI18nResourceEntity> batch = resources.subList(
                    startIndex, Math.min(resources.size(), startIndex + RELEASE_QUERY_BATCH));
            LambdaQueryWrapper<SystemI18nReleaseEntity> query =
                    Wrappers.<SystemI18nReleaseEntity>lambdaQuery();
            query.and(group -> {
                boolean first = true;
                for (SystemI18nResourceEntity resource : batch) {
                    if (first) {
                        group.eq(SystemI18nReleaseEntity::getResourceId, resource.getId())
                                .eq(SystemI18nReleaseEntity::getReleaseVersion,
                                        resource.getPublishedVersion());
                        first = false;
                    } else {
                        group.or(part -> part.eq(SystemI18nReleaseEntity::getResourceId, resource.getId())
                                .eq(SystemI18nReleaseEntity::getReleaseVersion,
                                        resource.getPublishedVersion()));
                    }
                }
            });
            releaseRows.addAll(releaseMapper.selectList(query));
        }

        Map<String, List<SystemI18nReleaseEntity>> byResource = new HashMap<>();
        releaseRows.forEach(release -> byResource
                .computeIfAbsent(release.getResourceId(), ignored -> new ArrayList<>()).add(release));
        Map<String, SystemI18nResourceEntity> resourceByCode = resources.stream().collect(
                java.util.stream.Collectors.toMap(SystemI18nResourceEntity::getResourceCode, value -> value));

        Set<Reference> result = new HashSet<>();
        for (Reference reference : references) {
            SystemI18nResourceEntity resource = resourceByCode.get(reference.resourceCode());
            if (resource == null) {
                continue;
            }
            List<SystemI18nReleaseEntity> current = byResource.getOrDefault(resource.getId(), List.of());
            if (current.size() != 2
                    || current.stream().map(SystemI18nReleaseEntity::getLocale).distinct().count() != 2) {
                continue;
            }
            boolean present = current.stream().allMatch(release -> decodeMessages(release.getMessagesJson())
                    .containsKey(reference.messageKey()));
            if (present) {
                result.add(reference);
            }
        }
        return Set.copyOf(result);
    }

    private Map<String, String> decodeMessages(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("持久化 I18n Release Snapshot 结构损坏", exception);
        }
    }
}
