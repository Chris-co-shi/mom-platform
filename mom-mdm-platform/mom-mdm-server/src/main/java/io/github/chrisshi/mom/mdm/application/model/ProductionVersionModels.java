package io.github.chrisshi.mom.mdm.application.model;

import java.time.Instant;
import java.time.LocalDate;

/** ProductionVersion 应用层输入输出，不暴露数据库 Entity 或 HTTP 信封。 */
public final class ProductionVersionModels {
    private ProductionVersionModels() { }

    /** 业务版本与技术锁版本分离的完整只读视图。 */
    public record View(String id, String code, String name, String businessVersion,
                       String plantId, String outputMaterialId,
                       String formulaRevisionId, String routingRevisionId,
                       LocalDate validFrom, LocalDate validTo, String status, Long lockVersion,
                       Instant releasedAt, Instant obsoleteAt, Instant createdAt, Instant updatedAt) { }

    /** 草稿可修改字段；Code 保持稳定身份。 */
    public record DraftInput(String name, String businessVersion, String plantId,
                             String outputMaterialId, String formulaRevisionId, String routingRevisionId,
                             LocalDate validFrom, LocalDate validTo) { }
}
