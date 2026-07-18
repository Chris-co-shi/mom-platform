package io.github.chrisshi.mom.messaging.autoconfigure;

import io.github.chrisshi.mom.messaging.event.EventTransport;
import io.github.chrisshi.mom.messaging.stream.StreamBridgeEventTransport;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;

/**
 * MOM 消息传输自动配置。
 *
 * <p>仅在 Spring Cloud Stream 的 {@link StreamBridge} 存在时注册默认 {@link EventTransport}。应用可以提供
 * 自定义实现覆盖默认适配器，例如在测试中使用确定性内存传输。该配置不声明 Topic、消费者组或重试策略，
 * 这些 Broker 相关参数必须由各服务的 Binding 配置显式决定。</p>
 */
@AutoConfiguration
@ConditionalOnClass(StreamBridge.class)
public class MomMessagingAutoConfiguration {

    /**
     * 创建默认 Spring Cloud Stream 事件传输适配器。
     *
     * @param streamBridge Spring Cloud Stream 输出桥接器
     * @return MOM 事件传输端口实现
     */
    @Bean
    @ConditionalOnMissingBean(EventTransport.class)
    EventTransport momStreamBridgeEventTransport(StreamBridge streamBridge) {
        return new StreamBridgeEventTransport(streamBridge);
    }
}
