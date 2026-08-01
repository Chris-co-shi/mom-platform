package io.github.chrisshi.mom.system.infrastructure.messaging;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.outbox.application.OutboxAppender;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 使用 System 本地事务 Outbox 追加 Runtime 变更事实。
 *
 * <p>Adapter 不直接调用 Broker。事件 Payload 只包含稳定 Code、状态、版本与 checksum，不包含参数值、翻译正文、
 * Dictionary Label、Permission Assignment、用户、Token、Secret 或数据库 Entity。</p>
 */
@Component
public class OutboxSystemRuntimeChangeEventAdapter implements SystemRuntimeChangeEventPort {
    public static final String CATALOG_PUBLISHED_EVENT = "system.catalog.published";
    public static final String CATALOG_STATUS_CHANGED_EVENT = "system.catalog.status-changed";
    public static final String PARAMETER_CHANGED_EVENT = "system.parameter.changed";
    public static final String DICTIONARY_CHANGED_EVENT = "system.dictionary.changed";
    public static final String I18N_PUBLISHED_EVENT = "system.i18n.published";
    public static final String I18N_STATUS_CHANGED_EVENT = "system.i18n.status-changed";

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
        append(
                CATALOG_PUBLISHED_EVENT,
                "SystemApplicationCatalog",
                event.applicationId(),
                new CatalogPublishedPayload(
                        event.applicationCode(),
                        event.releaseVersion(),
                        event.routeContractVersion(),
                        event.checksum(),
                        event.sourceReleaseVersion()));
    }

    @Override
    public void catalogStatusChanged(CatalogStatusChangedEvent event) {
        Objects.requireNonNull(event, "event");
        append(
                CATALOG_STATUS_CHANGED_EVENT,
                "SystemApplicationCatalog",
                event.applicationId(),
                new CatalogStatusChangedPayload(
                        event.applicationCode(),
                        event.version(),
                        event.enabled()));
    }

    @Override
    public void parameterChanged(ParameterChangedEvent event) {
        Objects.requireNonNull(event, "event");
        append(
                PARAMETER_CHANGED_EVENT,
                "SystemParameter",
                event.parameterId(),
                new ParameterChangedPayload(
                        event.parameterKey(),
                        event.scopeType().name(),
                        event.scopeCode(),
                        event.version(),
                        event.enabled(),
                        event.changeKind().name()));
    }

    @Override
    public void dictionaryChanged(DictionaryChangedEvent event) {
        Objects.requireNonNull(event, "event");
        append(
                DICTIONARY_CHANGED_EVENT,
                "SystemDictionary",
                event.dictionaryId(),
                new DictionaryChangedPayload(
                        event.dictionaryCode(),
                        event.itemCode(),
                        event.version(),
                        event.enabled(),
                        event.changeKind().name()));
    }

    @Override
    public void i18nPublished(I18nPublishedEvent event) {
        Objects.requireNonNull(event, "event");
        append(
                I18N_PUBLISHED_EVENT,
                "SystemI18nResource",
                event.resourceId(),
                new I18nPublishedPayload(
                        event.applicationCode(),
                        event.resourceCode(),
                        event.releaseVersion(),
                        event.checksums(),
                        event.sourceReleaseVersion()));
    }

    @Override
    public void i18nStatusChanged(I18nStatusChangedEvent event) {
        Objects.requireNonNull(event, "event");
        append(
                I18N_STATUS_CHANGED_EVENT,
                "SystemI18nResource",
                event.resourceId(),
                new I18nStatusChangedPayload(
                        event.applicationCode(),
                        event.resourceCode(),
                        event.version(),
                        event.enabled()));
    }

    private void append(
            String eventType,
            String aggregateType,
            String aggregateId,
            Object payload) {
        String eventId = UUID.randomUUID().toString();
        outbox.append(new EventEnvelope(
                eventId,
                eventType,
                1,
                aggregateType,
                aggregateId,
                clock.instant(),
                PRODUCER,
                eventId,
                encode(payload)));
    }

    private String encode(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法编码 System Runtime 变更事件", exception);
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
        I18nPublishedPayload {
            checksums = checksums == null ? Map.of() : Map.copyOf(checksums);
        }
    }

    record I18nStatusChangedPayload(
            String applicationCode,
            String resourceCode,
            long version,
            boolean enabled) {
    }
}
