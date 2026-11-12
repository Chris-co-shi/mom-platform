package io.github.chrisshi.mom.mdm.api;

import java.time.Instant;
import java.util.List;

/**
 * 已发布质量标准的稳定跨域只读快照，供 QMS 执行域创建检验任务时固定定义版本。
 *
 * <p>{@code availableForNewUse} 仅约束新业务；已保存的修订及条目仍是历史事实。</p>
 *
 * @param id 质量标准稳定 ID
 * @param code 业务编码
 * @param name 当前名称
 * @param plantId 适用工厂 ID
 * @param materialId 适用物料 ID
 * @param inspectionUsage 检验用途，V1 支持 INCOMING 与 FINISHED_GOODS
 * @param revisionId 当前已发布修订 ID；没有已发布修订时为空
 * @param revisionNo 当前已发布业务修订号；没有已发布修订时为空
 * @param releasedAt 发布时间；没有已发布修订时为空
 * @param items 已发布修订的不可变检验项快照
 * @param availableForNewUse 当前是否允许新业务引用
 * @param invalidReason 不可新引用时的稳定原因码
 */
public record QualitySpecificationReference(
        String id, String code, String name, String plantId, String materialId, String inspectionUsage,
        String revisionId, Integer revisionNo, Instant releasedAt, List<Item> items,
        boolean availableForNewUse, String invalidReason) {

    /** 已发布检验项及引用对象技术版本快照；数值使用 Decimal String。 */
    public record Item(
            Integer sequenceNo, String characteristicId, Long characteristicVersion,
            String methodId, Long methodVersion, String uomId, Long uomVersion,
            String lowerLimit, String targetValue, String upperLimit, boolean mandatory) { }
}
