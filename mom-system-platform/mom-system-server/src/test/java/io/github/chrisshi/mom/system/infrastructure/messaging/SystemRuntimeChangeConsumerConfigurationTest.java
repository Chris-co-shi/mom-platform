package io.github.chrisshi.mom.system.infrastructure.messaging;

import io.github.chrisshi.mom.outbox.application.InboxDeduplicator;
import io.github.chrisshi.mom.system.application.i18n.port.SystemI18nRuntimeCachePort;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** System Runtime Consumer 必须用 Boot Jackson 解析稳定 JSON，不依赖 Binder 特有 Fastjson 转换。 */
class SystemRuntimeChangeConsumerConfigurationTest {

    @Test
    void shouldDecodeBrokerBytesAndInvalidateLocalSystemRegion() {
        InboxDeduplicator inbox = mock(InboxDeduplicator.class);
        SystemRuntimeCachePort cache = mock(SystemRuntimeCachePort.class);
        SystemI18nRuntimeCachePort i18nCache = mock(SystemI18nRuntimeCachePort.class);
        var consumer = new SystemRuntimeChangeConsumerConfiguration().systemRuntimeChangeConsumer(
                inbox, cache, i18nCache, JsonMapper.builder().findAndAddModules().build());
        byte[] json = ("""
                {"eventId":"00000000-0000-0000-0000-000000000001",\
                "eventType":"system.parameter.changed","eventVersion":1,\
                "aggregateType":"SystemParameter","aggregateId":"1",\
                "occurredAt":"2026-08-01T00:00:00Z","producer":"mom-system-server",\
                "correlationId":"00000000-0000-0000-0000-000000000001",\
                "payloadJson":"{\\\"parameterKey\\\":\\\"system.locale\\\",\\\"scopeType\\\":\\\"GLOBAL\\\",\\\"scopeCode\\\":\\\"\\\",\\\"version\\\":1,\\\"enabled\\\":true,\\\"changeKind\\\":\\\"UPDATED\\\"}"}
                """).getBytes(StandardCharsets.UTF_8);

        consumer.accept(MessageBuilder.withPayload(json).build());

        verify(cache).evictParameter("system.locale");
    }
}
