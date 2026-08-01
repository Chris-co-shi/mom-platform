package io.github.chrisshi.mom.system.infrastructure.messaging;

import io.github.chrisshi.mom.messaging.event.EventType;

/**
 * System bounded context 拥有的运行时变更事件类型。
 *
 * <p>该枚举位于 System Messaging Adapter 边界，只用于生产和解析 System 自己的事件 Code。其他 bounded
 * context 不得引用本枚举；跨服务共享的是 {@link EventType}、EventEnvelope 与稳定字符串。枚举不可变且
 * 线程安全，新增事件必须有真实消费者和契约测试，不能为未来场景预留空值。</p>
 */
public enum SystemEventType implements EventType {

    SYSTEM_CATALOG_PUBLISHED("system.catalog.published"),
    SYSTEM_CATALOG_STATUS_CHANGED("system.catalog.status-changed"),
    SYSTEM_PARAMETER_CHANGED("system.parameter.changed"),
    SYSTEM_DICTIONARY_CHANGED("system.dictionary.changed"),
    SYSTEM_I18N_PUBLISHED("system.i18n.published"),
    SYSTEM_I18N_STATUS_CHANGED("system.i18n.status-changed");

    private final String code;

    SystemEventType(String code) {
        this.code = code;
    }

    /**
     * 返回现有数据库、Topic 和消费者使用的稳定字符串 Code。
     *
     * @return System 事件 Code
     */
    @Override
    public String code() {
        return code;
    }

    /**
     * 将线格式字符串解析为 System 本地枚举。
     *
     * @param code EventEnvelope 中的稳定 Code
     * @return 匹配的 System EventType
     * @throws IllegalArgumentException 未知 Code 时 fail-closed，消费者不得静默忽略协议漂移
     */
    public static SystemEventType fromCode(String code) {
        for (SystemEventType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知 System EventType: " + code);
    }
}
