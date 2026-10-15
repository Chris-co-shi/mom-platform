package io.github.chrisshi.mom.mdm.application;

import io.github.chrisshi.mom.mdm.application.model.QualityModels.ScopeSnapshot;
import io.github.chrisshi.mom.mdm.application.model.QualityModels.UomSnapshot;
import io.github.chrisshi.mom.mdm.infrastructure.entity.MaterialEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.PlantEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomCategoryEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.DimensionEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.MaterialMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.PlantMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomCategoryMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.DimensionMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 质量定义对 MDM 工厂、物料和单位的本地引用校验器。
 *
 * <p>该类只能由质量定义写事务调用；按主键行锁读取权威记录，防止校验后、
 * 提交前被并发停用。不访问 QMS Schema，不执行跨 Schema JOIN。</p>
 */
@Component
final class QualityDefinitionReferenceValidator {
    private final PlantMapper plants;
    private final MaterialMapper materials;
    private final UomMapper uoms;
    private final UomCategoryMapper uomCategories;
    private final DimensionMapper dimensions;

    QualityDefinitionReferenceValidator(PlantMapper plants, MaterialMapper materials,
                                        UomMapper uoms, UomCategoryMapper uomCategories,
                                        DimensionMapper dimensions) {
        this.plants = plants;
        this.materials = materials;
        this.uoms = uoms;
        this.uomCategories = uomCategories;
        this.dimensions = dimensions;
    }

    /** 锁定并校验工厂与物料当前可用，返回发布历史所需版本快照。 */
    ScopeSnapshot requireScope(String plantId, String materialId) {
        PlantEntity plant = plants.selectByIdForUpdate(QualityDefinitionRules.id(plantId));
        MaterialEntity material = materials.selectByIdForUpdate(QualityDefinitionRules.id(materialId));
        if (plant == null || material == null) throw MdmException.invalidReference();
        MdmMasterDataRules.requireEnabled(plant.getStatus(), "plant");
        MdmMasterDataRules.requireEnabled(material.getStatus(), "material");
        if (plant.getVersion() == null || material.getRevisionNo() == null || material.getVersion() == null) {
            throw MdmException.invalidReference();
        }
        return new ScopeSnapshot(plant.getId(), plant.getVersion(), material.getId(),
                material.getRevisionNo(), material.getVersion());
    }

    /** 锁定单位及其类别，校验启用状态并返回量纲快照。 */
    UomSnapshot requireUom(String uomId) {
        return requireUoms(java.util.List.of(uomId)).get(QualityDefinitionRules.id(uomId));
    }

    /** 按稳定 ID 顺序锁定多个单位及其类别，避免多检验项并发写的锁顺序反转。 */
    Map<String, UomSnapshot> requireUoms(Collection<String> uomIds) {
        Map<String, UomEntity> lockedUoms = new TreeMap<>();
        uomIds.stream().map(QualityDefinitionRules::id).distinct().sorted().forEach(id -> {
            UomEntity uom = uoms.selectByIdForUpdate(id);
            if (uom == null) throw MdmException.invalidReference();
            MdmMasterDataRules.requireEnabled(uom.getStatus(), "uom");
            lockedUoms.put(id, uom);
        });

        Map<String, UomCategoryEntity> lockedCategories = new TreeMap<>();
        lockedUoms.values().stream().map(UomEntity::getCategoryId).distinct().sorted().forEach(id -> {
            UomCategoryEntity category = uomCategories.selectByIdForUpdate(id);
            if (category == null) throw MdmException.invalidReference();
            MdmMasterDataRules.requireEnabled(category.getStatus(), "uom category");
            lockedCategories.put(id, category);
        });

        Map<String, UomSnapshot> snapshots = new LinkedHashMap<>();
        lockedUoms.forEach((id, uom) -> {
            UomCategoryEntity category = lockedCategories.get(uom.getCategoryId());
            if (uom.getVersion() == null || category == null || category.getDimensionId() == null) {
                throw MdmException.invalidReference();
            }
            snapshots.put(id, new UomSnapshot(id, uom.getVersion(), category.getDimensionId()));
        });
        return Map.copyOf(snapshots);
    }

    /** 只读判断质量标准范围当前是否仍可供新业务引用。 */
    String scopeInvalidReason(String plantId, String materialId) {
        PlantEntity plant = plants.selectById(plantId);
        if (plant == null) return "PLANT_MISSING";
        if (!MdmMasterDataRules.ENABLED.equals(plant.getStatus())) return "PLANT_DISABLED";
        MaterialEntity material = materials.selectById(materialId);
        if (material == null) return "MATERIAL_MISSING";
        return MdmMasterDataRules.ENABLED.equals(material.getStatus()) ? null : "MATERIAL_DISABLED";
    }

    /** 只读判断条目单位及其类别、量纲链当前是否仍有效。 */
    String uomInvalidReason(String uomId) {
        UomEntity uom = uoms.selectById(uomId);
        if (uom == null) return "UOM_MISSING";
        if (!MdmMasterDataRules.ENABLED.equals(uom.getStatus())) return "UOM_DISABLED";
        UomCategoryEntity category = uomCategories.selectById(uom.getCategoryId());
        if (category == null) return "UOM_CATEGORY_MISSING";
        if (!MdmMasterDataRules.ENABLED.equals(category.getStatus())) return "UOM_CATEGORY_DISABLED";
        DimensionEntity dimension = dimensions.selectById(category.getDimensionId());
        if (dimension == null) return "DIMENSION_MISSING";
        return MdmMasterDataRules.ENABLED.equals(dimension.getStatus()) ? null : "DIMENSION_DISABLED";
    }
}
