package io.github.chrisshi.mom.messaging.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 固定 Framework EventType 只暴露稳定 code，业务枚举不进入共享模块的契约。
 *
 * <p>测试使用本地枚举模拟 bounded context。信封写线时只保存字符串 code，消费者无需也不得依赖生产方
 * Java 枚举类型。</p>
 */
class EventTypeContractTest {

    @Test
    void shouldWriteLocalEventTypeAsStableStringCode() {
        EventEnvelope envelope = new EventEnvelope(
                "event-1",
                LocalEventType.ENTITY_CHANGED,
                1,
                "entity",
                "entity-1",
                Instant.EPOCH,
                "mom-test-server",
                "correlation-1",
                "{\"changed\":true}"
        );

        assertThat(envelope.eventType()).isEqualTo("test.entity.changed");
        assertThat(EventType.class.getDeclaredMethods()).extracting("name").containsExactly("code");
    }

    private enum LocalEventType implements EventType {
        ENTITY_CHANGED("test.entity.changed");

        private final String code;

        LocalEventType(String code) {
            this.code = code;
        }

        @Override
        public String code() {
            return code;
        }
    }
}
