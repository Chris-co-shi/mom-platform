package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

/**
 * MDM Seata AT 受控技术验证请求。
 *
 * <p>两个故障标识分别验证远端参与者本地失败和远端成功后的全局回滚。调用方不得同时设置两个标识，避免
 * 一个请求混合多个故障语义而降低验收结果的可解释性。</p>
 *
 * @param transactionKey 本次短全局事务的唯一技术键
 * @param coordinatorValue MDM 本地分支写入值
 * @param participantValue Integration 远端分支写入值
 * @param failParticipant 是否让 Integration 在本地 INSERT 后主动失败
 * @param failAfterParticipant 是否在 Integration 成功返回后由 MDM 主动触发全局回滚
 */
public record MdmSeataAtProbeRequest(
        String transactionKey,
        String coordinatorValue,
        String participantValue,
        boolean failParticipant,
        boolean failAfterParticipant) {
}
