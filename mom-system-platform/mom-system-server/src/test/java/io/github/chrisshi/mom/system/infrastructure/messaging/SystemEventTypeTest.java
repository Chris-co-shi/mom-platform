package io.github.chrisshi.mom.system.infrastructure.messaging;

import io.github.chrisshi.mom.messaging.event.EventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 固定 System bounded context 拥有的事件类型集合与共享契约边界。
 *
 * <p>该枚举只在 System 内使用；跨服务线格式仍是稳定字符串 code，其他 bounded context 必须建立自己的
 * 本地映射，不能引用 System 枚举。</p>
 */
class SystemEventTypeTest {

    @Test
    void shouldOwnExactlyTheFrozenSystemEventCodes() {
        assertThat(SystemEventType.class).isAssignableTo(EventType.class);
        assertThat(SystemEventType.values())
                .extracting(SystemEventType::name)
                .containsExactly(
                        "SYSTEM_CATALOG_PUBLISHED",
                        "SYSTEM_CATALOG_STATUS_CHANGED",
                        "SYSTEM_PARAMETER_CHANGED",
                        "SYSTEM_DICTIONARY_CHANGED",
                        "SYSTEM_I18N_PUBLISHED",
                        "SYSTEM_I18N_STATUS_CHANGED"
                );
        assertThat(SystemEventType.SYSTEM_CATALOG_PUBLISHED.code())
                .isEqualTo("system.catalog.published");
    }
}
