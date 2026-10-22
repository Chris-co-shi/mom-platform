package io.github.chrisshi.mom.mdm.application.model;

import java.time.Instant;
import java.util.List;

/** 配方用例的应用层输入和输出，不依赖 HTTP 信封或持久化 Entity。 */
public final class FormulaModels {
    private FormulaModels() { }

    /** 单项明细输入，数量保持 Decimal String 直到应用层明确校验。 */
    public record ItemInput(Integer sequenceNo, String materialId, String quantity, String uomId) { }

    /** 配方身份和批量基准的只读视图。 */
    public record FormulaView(String id, String code, String name, String plantId,
                              String outputMaterialId, String baseBatchQuantity, String baseUomId,
                              Instant createdAt, String createdBy, Instant updatedAt, String updatedBy,
                              List<ReleasedRevisionView> releasedRevisions) { }

    /** 当前仍可供生产版本选取的已发布修订摘要。 */
    public record ReleasedRevisionView(String id, Integer revisionNo) { }

    /** 业务修订号与技术锁版本分离的修订视图。 */
    public record RevisionView(String id, String formulaId, Integer revisionNo, String status,
                               Long lockVersion, Instant releasedAt, Instant obsoleteAt,
                               Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) { }

    /** 配方明细视图，数量始终作为精确 Decimal String 返回。 */
    public record ItemView(String id, Integer sequenceNo, String materialId, String quantity, String uomId) { }

    /** 包含一个具体修订和其明细的完整配方详情。 */
    public record DetailView(FormulaView formula, RevisionView revision, List<ItemView> items) { }
}
