package io.github.chrisshi.mom.system.infrastructure.messaging;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.outbox.application.InboxDeduplicator;
import io.github.chrisshi.mom.system.application.i18n.port.SystemI18nRuntimeCachePort;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * System Runtime 变更事件消费与 Cache 失效装配。
 *
 * <p>Inbox 去重事务只记录事件，不在数据库事务中调用 Redis。事务结束后执行幂等 Evict；Redis 失败向上抛出，
 * Broker 重投时即使 Inbox 已存在也会再次尝试 Evict，直至成功或进入 DLQ。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "mom.system.runtime-events.consumer",
        name = "enabled",
        havingValue = "true")
public class SystemRuntimeChangeConsumerConfiguration {
    public static final String CONSUMER_NAME = "mom-system-runtime-cache-invalidation-v1";

    @Bean
    Consumer<Message<EventEnvelope>> systemRuntimeChangeConsumer(
            InboxDeduplicator inbox,
            SystemRuntimeCachePort cache,
            SystemI18nRuntimeCachePort i18nCache,
            ObjectMapper objectMapper) {
        return message -> {
            EventEnvelope event = Objects.requireNonNull(message.getPayload(), "event");
            inbox.executeOnce(event, CONSUMER_NAME, () -> {
                // Inbox 事务只保存接收事实；Redis 必须在事务返回后调用。
            });
            invalidate(event, cache, i18nCache, objectMapper);
        };
    }

    private static void invalidate(
            EventEnvelope event,
            SystemRuntimeCachePort cache,
            SystemI18nRuntimeCachePort i18nCache,
            ObjectMapper objectMapper) {
        switch (SystemEventType.fromCode(event.eventType())) {
            case SYSTEM_CATALOG_PUBLISHED -> {
                requireVersionOne(event, "Catalog");
                CatalogPublishedPayload payload =
                        decode(event.payloadJson(), CatalogPublishedPayload.class, objectMapper, "Catalog");
                if (payload.applicationCode() == null || payload.applicationCode().isBlank()
                        || payload.releaseVersion() < 1
                        || payload.checksum() == null
                        || payload.checksum().length() != 64) {
                    throw new IllegalArgumentException("Catalog 发布事件负载非法");
                }
                cache.evictCatalog(payload.applicationCode());
            }
            case SYSTEM_CATALOG_STATUS_CHANGED -> {
                requireVersionOne(event, "Catalog");
                CatalogStatusChangedPayload payload = decode(
                        event.payloadJson(), CatalogStatusChangedPayload.class, objectMapper, "Catalog");
                if (payload.applicationCode() == null || payload.applicationCode().isBlank()
                        || payload.version() < 0) {
                    throw new IllegalArgumentException("Catalog 状态事件负载非法");
                }
                cache.evictCatalog(payload.applicationCode());
            }
            case SYSTEM_PARAMETER_CHANGED -> {
                requireVersionOne(event, "Parameter");
                ParameterChangedPayload payload =
                        decode(event.payloadJson(), ParameterChangedPayload.class, objectMapper, "Parameter");
                if (payload.parameterKey() == null || payload.parameterKey().isBlank()
                        || payload.version() < 0
                        || payload.changeKind() == null
                        || payload.changeKind().isBlank()) {
                    throw new IllegalArgumentException("Parameter 事件负载非法");
                }
                cache.evictParameter(payload.parameterKey());
            }
            case SYSTEM_DICTIONARY_CHANGED -> {
                requireVersionOne(event, "Dictionary");
                DictionaryChangedPayload payload =
                        decode(event.payloadJson(), DictionaryChangedPayload.class, objectMapper, "Dictionary");
                if (payload.dictionaryCode() == null || payload.dictionaryCode().isBlank()
                        || payload.version() < 0
                        || payload.changeKind() == null
                        || payload.changeKind().isBlank()) {
                    throw new IllegalArgumentException("Dictionary 事件负载非法");
                }
                cache.evictDictionary(payload.dictionaryCode());
            }
            case SYSTEM_I18N_PUBLISHED -> {
                requireVersionOne(event, "I18n");
                I18nPublishedPayload payload = decode(
                        event.payloadJson(), I18nPublishedPayload.class, objectMapper, "I18n");
                if (!validI18nCodes(payload.applicationCode(), payload.resourceCode())
                        || payload.releaseVersion() < 1
                        || payload.checksums() == null
                        || payload.checksums().size() != 2
                        || payload.checksums().values().stream().anyMatch(value ->
                                value == null || value.length() != 64)) {
                    throw new IllegalArgumentException("I18n 发布事件负载非法");
                }
                i18nCache.evict(payload.applicationCode(), payload.resourceCode());
            }
            case SYSTEM_I18N_STATUS_CHANGED -> {
                requireVersionOne(event, "I18n");
                I18nStatusChangedPayload payload = decode(
                        event.payloadJson(), I18nStatusChangedPayload.class, objectMapper, "I18n");
                if (!validI18nCodes(payload.applicationCode(), payload.resourceCode())
                        || payload.version() < 0) {
                    throw new IllegalArgumentException("I18n 状态事件负载非法");
                }
                i18nCache.evict(payload.applicationCode(), payload.resourceCode());
            }
        }
    }

    private static boolean validI18nCodes(String applicationCode, String resourceCode) {
        return applicationCode != null && !applicationCode.isBlank()
                && resourceCode != null && !resourceCode.isBlank();
    }

    private static void requireVersionOne(EventEnvelope event, String capability) {
        if (event.eventVersion() != 1) {
            throw new IllegalArgumentException("不支持的 " + capability + " 事件版本");
        }
    }

    private static <T> T decode(
            String json,
            Class<T> type,
            ObjectMapper objectMapper,
            String capability) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(capability + " 事件负载无法解析", exception);
        }
    }

    record CatalogPublishedPayload(
            String applicationCode,
            long releaseVersion,
            int routeContractVersion,
            String checksum,
            Long sourceReleaseVersion) {
    }

    record CatalogStatusChangedPayload(
            String applicationCode,
            long version,
            boolean enabled) {
    }

    record ParameterChangedPayload(
            String parameterKey,
            String scopeType,
            String scopeCode,
            long version,
            boolean enabled,
            String changeKind) {
    }

    record DictionaryChangedPayload(
            String dictionaryCode,
            String itemCode,
            long version,
            boolean enabled,
            String changeKind) {
    }

    record I18nPublishedPayload(
            String applicationCode,
            String resourceCode,
            long releaseVersion,
            Map<String, String> checksums,
            Long sourceReleaseVersion) {
    }

    record I18nStatusChangedPayload(
            String applicationCode,
            String resourceCode,
            long version,
            boolean enabled) {
    }
}
