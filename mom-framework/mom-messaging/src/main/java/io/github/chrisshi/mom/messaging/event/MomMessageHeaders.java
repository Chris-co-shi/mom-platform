package io.github.chrisshi.mom.messaging.event;

/**
 * MOM 消息头名称常量。
 *
 * <p>这些 Header 用于日志、Trace、错误通道和 Broker 管理工具快速识别事件，不替代消息体中的完整信封。
 * 消费者必须以 {@link EventEnvelope} 为权威内容，因为部分中间件或跨语言桥接可能过滤自定义 Header。</p>
 */
public final class MomMessageHeaders {

    /** 事件唯一标识 Header。 */
    public static final String EVENT_ID = "mom_event_id";

    /** 事件类型 Header。 */
    public static final String EVENT_TYPE = "mom_event_type";

    /** 事件契约版本 Header。 */
    public static final String EVENT_VERSION = "mom_event_version";

    /** 跨同步调用和异步消息的关联标识 Header。 */
    public static final String CORRELATION_ID = "mom_correlation_id";

    private MomMessageHeaders() {
    }
}
