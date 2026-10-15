package io.github.chrisshi.mom.qms.application.model;

/** QMS 分页过滤条件集合。 */
public final class QmsPageParams {
    private QmsPageParams() { }
    public record CharacteristicParams(Boolean enabled) { }
    public record MethodParams(Boolean enabled) { }
    public record SpecificationParams(String plantId, String materialId) { }
}
