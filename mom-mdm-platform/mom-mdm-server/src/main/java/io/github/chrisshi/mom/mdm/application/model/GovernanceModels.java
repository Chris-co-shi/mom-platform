package io.github.chrisshi.mom.mdm.application.model;

import java.time.Instant;
import java.util.List;

/** 主数据一致性检查的只读结果；不包含自动修复指令。 */
public final class GovernanceModels {
    private GovernanceModels() { }
    public enum Scope { ALL, ORGANIZATION, WAREHOUSE, MATERIAL_UOM, MANUFACTURING_DEFINITION, QUALITY_DEFINITION }
    public record Issue(String issueType, String resourceType, String resourceId, String businessCode,
                        String severity, String reasonCode, String relatedResourceId) { }
    public record Report(Scope scope, Instant checkedAt, int issueCount, boolean truncated, List<Issue> issues) { }
}
