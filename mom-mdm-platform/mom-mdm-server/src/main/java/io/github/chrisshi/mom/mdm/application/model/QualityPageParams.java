package io.github.chrisshi.mom.mdm.application.model;

/** QMS 分页过滤条件集合。 */
public final class QualityPageParams {
    private QualityPageParams() { }
    public record CharacteristicParams(Boolean enabled) { }
    public record MethodParams(Boolean enabled) { }
    public record SpecificationParams(String plantId, String materialId) { }
}
