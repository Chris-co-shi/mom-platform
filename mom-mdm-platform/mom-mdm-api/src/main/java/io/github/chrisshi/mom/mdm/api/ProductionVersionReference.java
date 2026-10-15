package io.github.chrisshi.mom.mdm.api;

import java.time.LocalDate;

/**
 * 已发布 ProductionVersion 的稳定跨域只读快照，供未来 MES 在建单时固定制造定义。
 *
 * @param id 稳定 String 主键
 * @param code 业务编码
 * @param name 当前名称
 * @param businessVersion 业务版本标识
 * @param plantId 工厂 ID
 * @param outputMaterialId 产出物料 ID
 * @param formulaRevisionId 已发布 FormulaRevision ID
 * @param routingRevisionId 已发布 RoutingRevision ID
 * @param validFrom 工厂业务日期起点（包含）
 * @param validTo 工厂业务日期终点（不包含），null 表示无穷远
 * @param version 技术锁版本
 * @param availableForNewUse 当前是否可供新工单引用
 * @param invalidReason 不可新引用时的稳定原因
 */
public record ProductionVersionReference(
        String id, String code, String name, String businessVersion,
        String plantId, String outputMaterialId, String formulaRevisionId, String routingRevisionId,
        LocalDate validFrom, LocalDate validTo, Long version,
        boolean availableForNewUse, String invalidReason) { }
