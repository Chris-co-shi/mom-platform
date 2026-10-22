package io.github.chrisshi.mom.mdm.application.model;

import java.time.Instant;
import java.util.List;

/** MDM 质量定义用例的不可变输入和输出，不依赖 HTTP Result 或数据库 Entity。 */
public final class QualityModels {
    private QualityModels() { }
    public record CharacteristicView(String id, String code, String name, String defaultUomId,
                                     long defaultUomVersion, boolean enabled, long version) { }
    public record MethodView(String id, String code, String name, String description,
                             boolean enabled, long version) { }
    public record SpecificationView(String id, String code, String name, String plantId, String materialId,
                                    String inspectionUsage, int latestRevisionNo, String latestStatus) { }
    public record ItemInput(Integer sequenceNo, String characteristicId, String methodId, String uomId,
                            String lowerLimit, String targetValue, String upperLimit, Boolean mandatory) { }
    public record ItemView(String id, int sequenceNo, String characteristicId, long characteristicVersion,
                           String methodId, long methodVersion, String uomId, long uomVersion,
                           String lowerLimit, String targetValue, String upperLimit, boolean mandatory) { }
    public record DetailView(String id, String code, String name, String plantId, String materialId,
                             String inspectionUsage, Long plantVersion, Integer materialRevisionNo,
                             Long materialVersion, String revisionId, int revisionNo, String status,
                             long lockVersion, Instant releasedAt, Instant obsoleteAt, List<ItemView> items) { }
    public record ScopeSnapshot(String plantId, long plantVersion, String materialId,
                                int materialRevisionNo, long materialVersion) { }
    public record UomSnapshot(String id, long version, String dimensionId) { }
    public record LocalItemReference(String characteristicId, long characteristicVersion,
                                     String defaultUomId, String methodId, long methodVersion) { }
    public record PreparedItem(int sequenceNo, String characteristicId, long characteristicVersion,
                               String methodId, long methodVersion, String uomId, long uomVersion,
                               String lowerLimit, String targetValue, String upperLimit, boolean mandatory) { }
}
