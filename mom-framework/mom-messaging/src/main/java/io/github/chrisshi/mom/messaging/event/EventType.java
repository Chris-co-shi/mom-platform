package io.github.chrisshi.mom.messaging.event;

/**
 * 业务事件类型到 Broker 无关稳定 Code 的最小契约。
 *
 * <p>Framework 不提供业务枚举；每个 bounded context 定义自己的本地枚举并实现本接口。跨服务只传输
 * {@link #code()} 字符串，消费者按字符串映射自己的本地模型，因此 System/IAM 等模块不会产生 Java 枚举
 * 依赖。实现通常为不可变枚举，可安全跨线程共享。</p>
 */
public interface EventType {

    /**
     * 返回跨服务线格式中的稳定事件 Code。
     *
     * @return 非空、版本无关的业务事件 Code；具体版本由 EventEnvelope 独立表达
     */
    String code();
}
