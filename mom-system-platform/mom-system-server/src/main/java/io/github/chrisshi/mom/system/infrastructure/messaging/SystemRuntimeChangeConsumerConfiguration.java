package io.github.chrisshi.mom.system.infrastructure.messaging;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.outbox.application.InboxDeduplicator;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
            ObjectMapper objectMapper) {
        return message -> {
            EventEnvelope event = Objects.requireNonNull(message.getPayload(), "event");
            inbox.executeOnce(event, CONSUMER_NAME, () -> {
                // Inbox 事务只保存接收事实；Redis 必须在事务返回后调用。
            });
            invalidate(event, cache, objectMapper);
        };
    }

    private static void invalidate(
            EventEnvelope event,
            SystemRuntimeCachePort cache,
            ObjectMapper objectMapper) {
        if (OutboxSystemRuntimeChangeEventAdapter.CATALOG_PUBLISHED_EVENT.equals(event.eventType())) {
            if (event.eventVersion() != 1) {
                throw new IllegalArgumentException("不支持的 Catalog 事件版本");
            }
            CatalogPublishedPayload payload = decode(event.payloadJson(), objectMapper);
            cache.evictCatalog(payload.applicationCode());
        }
    }

    private static CatalogPublishedPayload decode(String json, ObjectMapper objectMapper) {
        try {
            CatalogPublishedPayload payload = objectMapper.readValue(json, CatalogPublishedPayload.class);
            if (payload.applicationCode() == null || payload.applicationCode().isBlank()
                    || payload.releaseVersion() < 1 || payload.checksum() == null
                    || payload.checksum().length() != 64) {
                throw new IllegalArgumentException("Catalog 事件负载非法");
            }
            return payload;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Catalog 事件负载无法解析", exception);
        }
    }

    /** 与生产者 v1 Payload 对齐的本地消费模型。 */
    record CatalogPublishedPayload(
            String applicationCode,
            long releaseVersion,
            int routeContractVersion,
            String checksum,
            Long sourceReleaseVersion) {
    }
}
