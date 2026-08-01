package io.github.chrisshi.mom.system.infrastructure.messaging;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.outbox.application.OutboxAppender;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * 使用 System 本地事务 Outbox 追加 Runtime 变更事实。
 *
 * <p>Adapter 不直接调用 Broker。事件 Payload 只包含稳定 Code、版本与 checksum，不包含导航正文、Permission
 * Assignment、用户、Token、Secret 或数据库 Entity。</p>
 */
@Component
public class OutboxSystemRuntimeChangeEventAdapter implements SystemRuntimeChangeEventPort {
    public static final String CATALOG_PUBLISHED_EVENT = "system.catalog.published";
    private static final String PRODUCER = "mom-system-server";

    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxSystemRuntimeChangeEventAdapter(
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void catalogPublished(CatalogPublishedEvent event) {
        Objects.requireNonNull(event, "event");
        String eventId = UUID.randomUUID().toString();
        outbox.append(new EventEnvelope(
                eventId,
                CATALOG_PUBLISHED_EVENT,
                1,
                "SystemApplicationCatalog",
                event.applicationId(),
                clock.instant(),
                PRODUCER,
                eventId,
                encode(new CatalogPublishedPayload(
                        event.applicationCode(), event.releaseVersion(), event.routeContractVersion(),
                        event.checksum(), event.sourceReleaseVersion()))));
    }

    private String encode(CatalogPublishedPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法编码 System Runtime 变更事件", exception);
        }
    }

    /** 不含敏感正文的 Catalog 发布事件负载。 */
    record CatalogPublishedPayload(
            String applicationCode,
            long releaseVersion,
            int routeContractVersion,
            String checksum,
            Long sourceReleaseVersion) {
    }
}
