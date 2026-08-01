package io.github.chrisshi.mom.system.infrastructure.cache.redis;

import io.github.chrisshi.mom.system.application.i18n.port.SystemI18nRuntimeCachePort;
import io.github.chrisshi.mom.system.application.i18n.port.SystemI18nRuntimeQueryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Redis Dynamic I18n 不可变 Release Projection Cache。 */
@Component
public class RedisSystemI18nRuntimeCacheAdapter implements SystemI18nRuntimeCachePort {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(RedisSystemI18nRuntimeCacheAdapter.class);
    private static final Duration RELEASE_TTL = Duration.ofHours(12);
    private static final Duration INDEX_EXTRA_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String environment;

    public RedisSystemI18nRuntimeCacheAdapter(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${mom.system.runtime-cache.enabled:false}") boolean enabled,
            @Value("${mom.system.runtime-cache.environment:local}") String environment) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.enabled = enabled;
        this.environment = requireEnvironment(environment);
    }

    @Override
    public Optional<SystemI18nRuntimeQueryPort.RuntimeSnapshot> find(
            SystemI18nRuntimeQueryPort.RuntimeHeader header) {
        Objects.requireNonNull(header, "header");
        if (!enabled) {
            return Optional.empty();
        }
        String key = key(header);
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            SystemI18nRuntimeQueryPort.RuntimeSnapshot snapshot = objectMapper.readValue(
                    json, SystemI18nRuntimeQueryPort.RuntimeSnapshot.class);
            return matches(header, snapshot) ? Optional.of(snapshot) : Optional.empty();
        } catch (JacksonException exception) {
            readFailure(key, exception);
            return Optional.empty();
        } catch (RuntimeException exception) {
            readFailure(key, exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(
            SystemI18nRuntimeQueryPort.RuntimeHeader header,
            SystemI18nRuntimeQueryPort.RuntimeSnapshot snapshot) {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!enabled) {
            return;
        }
        if (!matches(header, snapshot)) {
            throw new IllegalArgumentException("I18n Cache Snapshot 与 PostgreSQL Header 不一致");
        }
        String key = key(header);
        String index = index(header.applicationCode(), header.resourceCode());
        Duration ttl = RELEASE_TTL.plusMinutes(
                Math.floorMod(header.checksum().hashCode(), 31));
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(snapshot), ttl);
            redis.opsForSet().add(index, key);
            redis.expire(index, ttl.plus(INDEX_EXTRA_TTL));
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法编码 I18n Runtime Cache Projection", exception);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "System I18n Runtime Cache 写入失败，权威 PostgreSQL 保持不变。failureType={}",
                    exception.getClass().getSimpleName());
        }
    }

    @Override
    public void evict(String applicationCode, String resourceCode) {
        if (!enabled) {
            return;
        }
        String index = index(applicationCode, resourceCode);
        try {
            Set<String> keys = redis.opsForSet().members(index);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
            redis.delete(index);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "System I18n Runtime Cache 失效失败，等待可靠事件重投或 TTL 修复。failureType={}",
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private void readFailure(String key, Exception exception) {
        LOGGER.warn(
                "System I18n Runtime Cache 读取失败，回源 PostgreSQL。failureType={}",
                exception.getClass().getSimpleName());
        bestEffortDelete(key);
    }

    private String key(SystemI18nRuntimeQueryPort.RuntimeHeader header) {
        return prefix() + "i18n-release:v1:"
                + header.applicationCode() + ':'
                + header.resourceCode() + ':'
                + header.releaseVersion() + ':'
                + header.locale() + ':'
                + header.checksum();
    }

    private String index(String applicationCode, String resourceCode) {
        return prefix() + "i18n-release-index:v1:"
                + applicationCode + ':' + resourceCode;
    }

    private String prefix() {
        return "mom:" + environment + ":system:";
    }

    private void bestEffortDelete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException ignored) {
            // 原始失败已记录；可靠失效事件或 TTL 最终修复。
        }
    }

    private static boolean matches(
            SystemI18nRuntimeQueryPort.RuntimeHeader header,
            SystemI18nRuntimeQueryPort.RuntimeSnapshot snapshot) {
        return header.applicationCode().equals(snapshot.applicationCode())
                && header.resourceCode().equals(snapshot.resourceCode())
                && header.locale().equals(snapshot.locale())
                && header.defaultLocale().equals(snapshot.defaultLocale())
                && header.releaseVersion() == snapshot.releaseVersion()
                && header.checksum().equals(snapshot.checksum())
                && header.fallbackCount() == snapshot.fallbackCount()
                && header.publishedAt().equals(snapshot.publishedAt());
    }

    private static String requireEnvironment(String value) {
        if (value == null || value.isBlank()
                || !value.matches("[a-z0-9][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException("runtime-cache environment 格式非法");
        }
        return value;
    }
}
