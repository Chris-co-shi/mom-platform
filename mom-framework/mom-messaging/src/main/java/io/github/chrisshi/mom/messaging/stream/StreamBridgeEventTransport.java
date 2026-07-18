package io.github.chrisshi.mom.messaging.stream;

import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.messaging.event.EventTransport;
import io.github.chrisshi.mom.messaging.event.MomMessageHeaders;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

import java.util.Objects;

/**
 * 基于 Spring Cloud Stream {@link StreamBridge} 的事件传输实现。
 *
 * <p>该适配器使用预先声明的 Binding 名称发送事件，不根据用户输入动态创建 Topic，避免无限动态 Binding
 * 导致资源泄漏。StreamBridge 默认在调用线程同步执行发送；具体 RocketMQ Producer 在 P01-S05 配置为同步
 * 发送，因此 Outbox 只有在调用返回 {@code true} 后才尝试标记 SENT。</p>
 *
 * <p>返回成功只表示 Broker 传输阶段被接受，不表示消费者业务已完成。消费者仍必须通过 Inbox 或唯一约束
 * 处理至少一次投递。该类型无可变状态，可被多个发布线程共享。</p>
 */
public final class StreamBridgeEventTransport implements EventTransport {

    private final StreamBridge streamBridge;

    /**
     * 创建 Spring Cloud Stream 事件传输适配器。
     *
     * @param streamBridge Spring Cloud Stream 动态输出桥接器
     */
    public StreamBridgeEventTransport(StreamBridge streamBridge) {
        this.streamBridge = Objects.requireNonNull(streamBridge, "streamBridge 不能为空");
    }

    /**
     * 构造包含稳定 MOM Header 的 JSON 消息并同步交给指定 Binding。
     *
     * @param bindingName 已配置的输出 Binding 名称
     * @param event 待发送事件信封
     * @return Binding 接受消息返回 {@code true}
     * @throws IllegalArgumentException Binding 名称为空时抛出
     * @throws RuntimeException Binder 或 Broker 发送失败时透传
     */
    @Override
    public boolean send(String bindingName, EventEnvelope event) {
        if (bindingName == null || bindingName.isBlank()) {
            throw new IllegalArgumentException("bindingName 不能为空");
        }
        Objects.requireNonNull(event, "event 不能为空");

        Message<EventEnvelope> message = MessageBuilder.withPayload(event)
                .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                .setHeader(MomMessageHeaders.EVENT_ID, event.eventId())
                .setHeader(MomMessageHeaders.EVENT_TYPE, event.eventType())
                .setHeader(MomMessageHeaders.EVENT_VERSION, event.eventVersion())
                .setHeader(MomMessageHeaders.CORRELATION_ID, event.correlationId())
                .build();
        return streamBridge.send(bindingName.trim(), message);
    }
}
