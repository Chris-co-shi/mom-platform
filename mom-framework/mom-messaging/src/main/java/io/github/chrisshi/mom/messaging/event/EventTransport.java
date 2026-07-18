package io.github.chrisshi.mom.messaging.event;

/**
 * MOM 事件传输端口。
 *
 * <p>该接口隔离 Outbox 发布流程与具体消息中间件。调用方只负责指定稳定 Binding 名称并提交完整事件信封，
 * 实现可以使用 Spring Cloud Stream、测试内存适配器或后续其他 Binder。接口不提供事务语义：业务事务只负责
 * 把事件写入 Outbox，真正的 Broker 网络调用必须在数据库短事务之外执行。</p>
 */
@FunctionalInterface
public interface EventTransport {

    /**
     * 将事件交给指定消息 Binding。
     *
     * <p>实现应保持同步调用语义，使返回值能够表达本次发送是否被 Binding 接受。返回 {@code false} 或抛出
     * 运行时异常都表示 Outbox 不得标记为已发送，必须进入可恢复重试流程。本方法不保证消费者已经完成处理。</p>
     *
     * @param bindingName Spring Cloud Stream 输出 Binding 名称
     * @param event 待发送的不可变事件信封
     * @return Binding 接受消息返回 {@code true}；未接受返回 {@code false}
     * @throws RuntimeException Binder 或 Broker 发送失败时允许抛出
     */
    boolean send(String bindingName, EventEnvelope event);
}
