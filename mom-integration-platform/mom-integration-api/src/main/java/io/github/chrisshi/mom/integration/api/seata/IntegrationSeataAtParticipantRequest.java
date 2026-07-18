package io.github.chrisshi.mom.integration.api.seata;

/**
 * Integration Seata AT 技术参与者写入请求。
 *
 * <p>该契约只服务于 Phase 01 的受控兼容性验证，不代表正式 Integration Hub 领域接口。请求刻意只包含
 * 一个稳定技术键、一个验证值和故障注入标识，避免把正式制造业务、数据库实体或 Seata 内部类型暴露到
 * 跨服务 API。</p>
 *
 * @param transactionKey 本次短事务的稳定技术键；调用链内必须唯一
 * @param participantValue Integration 参与者需要写入的技术验证值
 * @param failParticipant 是否在参与者本地写入后主动抛出异常，用于验证分支事务回滚
 */
public record IntegrationSeataAtParticipantRequest(
        String transactionKey,
        String participantValue,
        boolean failParticipant) {
}
