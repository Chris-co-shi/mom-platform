package io.github.chrisshi.mom.outbox.model;

/**
 * Outbox 记录持久化状态。
 *
 * <p>状态转换通过带当前状态和租约所有者条件的 SQL 完成，禁止先查询再无条件更新。{@link #CLAIMED}
 * 是短期领取状态，不代表消息已经进入 Broker；只有同步传输成功后才能进入 {@link #SENT}。</p>
 */
public enum OutboxStatus {
    /** 已写入本地事务，等待首次发布。 */
    PENDING,
    /** 已被某个发布实例租约领取，正在事务外调用 Broker。 */
    CLAIMED,
    /** 最近一次发布失败，等待退避时间到达。 */
    RETRY,
    /** Broker 传输已成功接受。 */
    SENT,
    /** 已达到最大尝试次数，需要人工检查或补偿。 */
    DEAD
}
