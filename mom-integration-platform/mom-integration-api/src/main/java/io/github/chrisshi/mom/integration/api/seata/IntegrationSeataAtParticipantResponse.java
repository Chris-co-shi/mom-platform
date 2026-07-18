package io.github.chrisshi.mom.integration.api.seata;

/**
 * Integration Seata AT 技术参与者执行结果。
 *
 * <p>响应返回参与者观察到的 XID，用于在 PoC 中证明 Spring Cloud Alibaba 已通过同步调用传播全局事务
 * 上下文。XID 仅用于诊断和自动化验收，不应作为业务标识、幂等键或长期持久化契约。</p>
 *
 * @param transactionKey 本次技术事务键
 * @param xid Integration 参与者线程观察到的 Seata 全局事务标识
 * @param status 技术参与者结果；当前成功值固定为 {@code RECORDED}
 */
public record IntegrationSeataAtParticipantResponse(
        String transactionKey,
        String xid,
        String status) {
}
