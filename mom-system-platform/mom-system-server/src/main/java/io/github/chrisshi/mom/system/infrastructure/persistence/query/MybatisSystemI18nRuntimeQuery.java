package io.github.chrisshi.mom.system.infrastructure.persistence.query;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.system.application.i18n.port.SystemI18nRuntimeQueryPort;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemI18nReleaseEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemI18nResourceEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.mapper.SystemI18nReleaseMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.mapper.SystemI18nResourceMapper;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis-Plus Dynamic I18n Runtime Query Adapter。
 *
 * <p>Header 路径显式选择最小列，不读取 messages_json；完整 Snapshot 路径只读取一个已确认 Locale 行。没有
 * Mapper XML、注解 SQL、跨 Schema JOIN、JdbcTemplate 或 {@code SELECT *}。</p>
 */
@Component
public class MybatisSystemI18nRuntimeQuery implements SystemI18nRuntimeQueryPort {
    private final SystemI18nResourceMapper resources;
    private final SystemI18nReleaseMapper releases;
    private final ObjectMapper objectMapper;

    public MybatisSystemI18nRuntimeQuery(
            SystemI18nResourceMapper resources,
            SystemI18nReleaseMapper releases,
            ObjectMapper objectMapper) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.releases = Objects.requireNonNull(releases, "releases");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public Optional<RuntimeHeader> findHeader(
            String applicationCode,
            String resourceCode,
            String locale) {
        SystemI18nResourceEntity resource = resources.selectOne(
                Wrappers.<SystemI18nResourceEntity>lambdaQuery()
                        .select(
                                SystemI18nResourceEntity::getId,
                                SystemI18nResourceEntity::getApplicationCode,
                                SystemI18nResourceEntity::getResourceCode,
                                SystemI18nResourceEntity::getDefaultLocale,
                                SystemI18nResourceEntity::getEnabled,
                                SystemI18nResourceEntity::getPublishedVersion)
                        .eq(SystemI18nResourceEntity::getApplicationCode, applicationCode)
                        .eq(SystemI18nResourceEntity::getResourceCode, resourceCode));
        if (resource == null
                || !Boolean.TRUE.equals(resource.getEnabled())
                || resource.getPublishedVersion() == null) {
            return Optional.empty();
        }

        long localeCount = releases.selectCount(
                Wrappers.<SystemI18nReleaseEntity>lambdaQuery()
                        .eq(SystemI18nReleaseEntity::getResourceId, resource.getId())
                        .eq(SystemI18nReleaseEntity::getReleaseVersion, resource.getPublishedVersion()));
        if (localeCount != 2) {
            return Optional.empty();
        }

        SystemI18nReleaseEntity release = releases.selectOne(
                Wrappers.<SystemI18nReleaseEntity>lambdaQuery()
                        .select(
                                SystemI18nReleaseEntity::getResourceId,
                                SystemI18nReleaseEntity::getReleaseVersion,
                                SystemI18nReleaseEntity::getLocale,
                                SystemI18nReleaseEntity::getChecksum,
                                SystemI18nReleaseEntity::getFallbackCount,
                                SystemI18nReleaseEntity::getPublishedAt)
                        .eq(SystemI18nReleaseEntity::getResourceId, resource.getId())
                        .eq(SystemI18nReleaseEntity::getReleaseVersion, resource.getPublishedVersion())
                        .eq(SystemI18nReleaseEntity::getLocale, locale));
        if (release == null) {
            return Optional.empty();
        }
        return Optional.of(new RuntimeHeader(
                resource.getId(),
                resource.getApplicationCode(),
                resource.getResourceCode(),
                resource.getDefaultLocale(),
                release.getReleaseVersion(),
                release.getLocale(),
                release.getChecksum(),
                release.getFallbackCount(),
                release.getPublishedAt()));
    }

    @Override
    public Optional<RuntimeSnapshot> findSnapshot(RuntimeHeader header) {
        Objects.requireNonNull(header, "header");
        SystemI18nReleaseEntity release = releases.selectOne(
                Wrappers.<SystemI18nReleaseEntity>lambdaQuery()
                        .eq(SystemI18nReleaseEntity::getResourceId, header.resourceId())
                        .eq(SystemI18nReleaseEntity::getReleaseVersion, header.releaseVersion())
                        .eq(SystemI18nReleaseEntity::getLocale, header.locale()));
        if (release == null
                || !Objects.equals(release.getChecksum(), header.checksum())
                || !Objects.equals(release.getFallbackCount(), header.fallbackCount())
                || !Objects.equals(release.getPublishedAt(), header.publishedAt())) {
            return Optional.empty();
        }
        String json = release.getMessagesJson();
        if (json == null || !sha256(json).equals(header.checksum())) {
            throw new IllegalStateException("I18n Release checksum 不一致");
        }
        return Optional.of(new RuntimeSnapshot(
                header.applicationCode(),
                header.resourceCode(),
                header.locale(),
                header.defaultLocale(),
                header.releaseVersion(),
                header.checksum(),
                header.fallbackCount(),
                header.publishedAt(),
                parseMessages(json)));
    }

    private Map<String, String> parseMessages(String json) {
        try {
            Object value = objectMapper.readValue(json, Map.class);
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IllegalStateException("I18n Release JSON Root 不是 Object");
            }
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> {
                if (!(key instanceof String textKey) || !(item instanceof String textValue)) {
                    throw new IllegalStateException("I18n Release JSON 必须是字符串键值 Object");
                }
                result.put(textKey, textValue);
            });
            return Map.copyOf(result);
        } catch (JacksonException exception) {
            throw new IllegalStateException("I18n Release JSON 损坏", exception);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }
}
