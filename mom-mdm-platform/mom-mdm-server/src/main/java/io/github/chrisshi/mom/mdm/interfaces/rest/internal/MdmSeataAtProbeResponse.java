package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

/**
 * MDM Seata AT 技术验证成功响应。
 *
 * <p>只有两个数据库分支均成功且全局事务准备提交时才返回。故障注入场景通过异常和 HTTP 5xx 表达，并由
 * 自动化测试直接检查两个独立数据库的最终状态，避免把“收到错误响应”误当作“已经完成回滚”。</p>
 *
 * @param transactionKey 本次技术事务键
 * @param coordinatorXid MDM 本地分支观察到的 XID
 * @param participantXid Integration 远端分支观察到的 XID
 * @param status 成功状态；当前固定为 {@code COMMITTING}
 */
public record MdmSeataAtProbeResponse(
        String transactionKey,
        String coordinatorXid,
        String participantXid,
        String status) {
}
