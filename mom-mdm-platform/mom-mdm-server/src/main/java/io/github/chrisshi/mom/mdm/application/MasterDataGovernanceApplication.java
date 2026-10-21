package io.github.chrisshi.mom.mdm.application;

import io.github.chrisshi.mom.mdm.application.model.GovernanceModels.*;
import io.github.chrisshi.mom.mdm.infrastructure.entity.*;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.*;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MDM 单 Schema 的按需一致性扫描。
 *
 * <p>该用例只读取当前主档和制造定义，不锁行、不自动修复、不访问其他业务域。扫描结果是诊断快照，
 * 不能替代创建/发布命令中的实时引用校验；单次最多返回 1000 个问题以保护管理请求。</p>
 */
@Component
@RequiredArgsConstructor
public class MasterDataGovernanceApplication {
    private static final int ISSUE_LIMIT = 1000;
    private static final String ENABLED = "ENABLED";
    private final PlantMapper plants; private final WorkshopMapper workshops; private final ProductionLineMapper lines;
    private final WorkstationMapper stations; private final WarehouseMapper warehouses; private final WarehouseAreaMapper areas;
    private final LocationMapper locations; private final LocationTypeMapper locationTypes;
    private final MaterialCategoryMapper categories; private final MaterialMapper materials;
    private final DimensionMapper dimensions; private final UomCategoryMapper uomCategories; private final UomMapper uoms;
    private final FormulaMapper formulas; private final FormulaRevisionMapper formulaRevisions; private final FormulaItemMapper formulaItems;
    private final RoutingMapper routings; private final RoutingRevisionMapper routingRevisions; private final RoutingStepMapper routingSteps;
    private final ProductionVersionMapper productionVersions;
    private final InspectionCharacteristicMapper inspectionCharacteristics; private final InspectionMethodMapper inspectionMethods;
    private final QualitySpecificationMapper qualitySpecifications; private final SpecificationRevisionMapper specificationRevisions;
    private final SpecificationItemMapper specificationItems;

    /** 执行所选范围的一致性扫描；ALL 按稳定板块顺序合并。 */
    @Transactional(readOnly = true)
    public Report check(Scope scope) {
        Scope selected = scope == null ? Scope.ALL : scope;
        Collector collector = new Collector();
        if (selected == Scope.ALL || selected == Scope.ORGANIZATION) organization(collector);
        if (selected == Scope.ALL || selected == Scope.WAREHOUSE) warehouse(collector);
        if (selected == Scope.ALL || selected == Scope.MATERIAL_UOM) materialUom(collector);
        if (selected == Scope.ALL || selected == Scope.MANUFACTURING_DEFINITION) manufacturing(collector);
        if (selected == Scope.ALL || selected == Scope.QUALITY_DEFINITION) quality(collector);
        return new Report(selected, Instant.now(), collector.issues.size(), collector.truncated, collector.issues);
    }

    private void organization(Collector out) {
        Map<String,PlantEntity> plantMap=map(plants.selectList(null));
        Map<String,WorkshopEntity> workshopMap=map(workshops.selectList(null));
        Map<String,ProductionLineEntity> lineMap=map(lines.selectList(null));
        plantMap.values().stream().filter(this::enabled).filter(p->p.getTimeZoneId()==null||p.getTimeZoneId().isBlank())
                .forEach(p->out.add("RESOURCE_VALIDITY","PLANT",p,"ERROR","PLANT_TIME_ZONE_MISSING",null));
        workshopMap.values().stream().filter(this::enabled).forEach(row->parent(out,"WORKSHOP",row,row.getPlantId(),plantMap,"PLANT"));
        lineMap.values().stream().filter(this::enabled).forEach(row->parent(out,"PRODUCTION_LINE",row,row.getWorkshopId(),workshopMap,"WORKSHOP"));
        stations.selectList(null).stream().filter(this::enabled).forEach(row->parent(out,"WORKSTATION",row,row.getProductionLineId(),lineMap,"PRODUCTION_LINE"));
    }

    private void warehouse(Collector out) {
        Map<String,PlantEntity> plantMap=map(plants.selectList(null)); Map<String,WarehouseEntity> warehouseMap=map(warehouses.selectList(null));
        Map<String,WarehouseAreaEntity> areaMap=map(areas.selectList(null)); Map<String,LocationTypeEntity> typeMap=map(locationTypes.selectList(null));
        warehouseMap.values().stream().filter(this::enabled).forEach(row->parent(out,"WAREHOUSE",row,row.getPlantId(),plantMap,"PLANT"));
        areaMap.values().stream().filter(this::enabled).forEach(row->parent(out,"WAREHOUSE_AREA",row,row.getWarehouseId(),warehouseMap,"WAREHOUSE"));
        locations.selectList(null).stream().filter(this::enabled).forEach(row->{
            parent(out,"LOCATION",row,row.getPlantId(),plantMap,"PLANT"); parent(out,"LOCATION",row,row.getLocationTypeId(),typeMap,"LOCATION_TYPE");
            if(row.getWarehouseAreaId()!=null){WarehouseAreaEntity area=areaMap.get(row.getWarehouseAreaId());
                if(area==null){out.add("REFERENCE","LOCATION",row,"ERROR","WAREHOUSE_AREA_MISSING",row.getWarehouseAreaId());return;}
                WarehouseEntity warehouse=warehouseMap.get(area.getWarehouseId());
                if(warehouse==null)out.add("REFERENCE","LOCATION",row,"ERROR","WAREHOUSE_MISSING",area.getWarehouseId());
                else if(!row.getPlantId().equals(warehouse.getPlantId()))out.add("OWNERSHIP","LOCATION",row,"ERROR","LOCATION_WAREHOUSE_PLANT_MISMATCH",warehouse.getId());
                else if(!enabled(area)||!enabled(warehouse))out.add("REFERENCE","LOCATION",row,"ERROR","WAREHOUSE_CHAIN_DISABLED",area.getId());
            }
        });
    }

    private void materialUom(Collector out) {
        Map<String,DimensionEntity> dimensionMap=map(dimensions.selectList(null)); Map<String,UomCategoryEntity> categoryMap=map(uomCategories.selectList(null));
        Map<String,UomEntity> uomMap=map(uoms.selectList(null)); Map<String,MaterialCategoryEntity> materialCategoryMap=map(categories.selectList(null));
        categoryMap.values().stream().filter(this::enabled).forEach(row->parent(out,"UOM_CATEGORY",row,row.getDimensionId(),dimensionMap,"DIMENSION"));
        uomMap.values().stream().filter(this::enabled).forEach(row->parent(out,"UOM",row,row.getCategoryId(),categoryMap,"UOM_CATEGORY"));
        categoryMap.values().stream().filter(this::enabled).forEach(category->{long references=uomMap.values().stream().filter(this::enabled)
                .filter(u->category.getId().equals(u.getCategoryId())&&Boolean.TRUE.equals(u.getReferenceUnit())).count();
            if(references!=1)out.add("UOM_BASE_CHAIN","UOM_CATEGORY",category,"ERROR",references==0?"REFERENCE_UOM_MISSING":"MULTIPLE_REFERENCE_UOMS",null);});
        materialCategoryMap.values().stream().filter(this::enabled).filter(row->row.getParentId()!=null)
                .forEach(row->parent(out,"MATERIAL_CATEGORY",row,row.getParentId(),materialCategoryMap,"MATERIAL_CATEGORY"));
        materials.selectList(null).stream().filter(this::enabled).forEach(row->{
            parent(out,"MATERIAL",row,row.getCategoryId(),materialCategoryMap,"MATERIAL_CATEGORY");
            UomEntity uom=uomMap.get(row.getBaseUomId());
            if(uom==null)out.add("REFERENCE","MATERIAL",row,"ERROR","BASE_UOM_MISSING",row.getBaseUomId());
            else {UomCategoryEntity category=categoryMap.get(uom.getCategoryId()); DimensionEntity dimension=category==null?null:dimensionMap.get(category.getDimensionId());
                if(!enabled(uom)||!enabled(category)||!enabled(dimension))out.add("REFERENCE","MATERIAL",row,"ERROR","BASE_UOM_CHAIN_DISABLED",uom.getId());}
        });
    }

    private void manufacturing(Collector out) {
        Map<String,PlantEntity> plantMap=map(plants.selectList(null));Map<String,MaterialEntity> materialMap=map(materials.selectList(null));Map<String,UomEntity> uomMap=map(uoms.selectList(null));
        Map<String,ProductionLineEntity> lineMap=map(lines.selectList(null));Map<String,WorkstationEntity> stationMap=map(stations.selectList(null));
        Map<String,FormulaEntity> formulaMap=map(formulas.selectList(null));Map<String,FormulaRevisionEntity> formulaRevisionMap=map(formulaRevisions.selectList(null));
        Map<String,RoutingEntity> routingMap=map(routings.selectList(null));Map<String,RoutingRevisionEntity> routingRevisionMap=map(routingRevisions.selectList(null));
        formulaMap.values().forEach(row->{required(out,"FORMULA",row,row.getPlantId(),plantMap,"PLANT_MISSING");required(out,"FORMULA",row,row.getOutputMaterialId(),materialMap,"OUTPUT_MATERIAL_MISSING");required(out,"FORMULA",row,row.getBaseUomId(),uomMap,"BASE_UOM_MISSING");});
        formulaItems.selectList(null).forEach(row->{required(out,"FORMULA_ITEM",row,row.getRevisionId(),formulaRevisionMap,"REVISION_MISSING");required(out,"FORMULA_ITEM",row,row.getMaterialId(),materialMap,"MATERIAL_MISSING");required(out,"FORMULA_ITEM",row,row.getUomId(),uomMap,"UOM_MISSING");});
        routingMap.values().forEach(row->required(out,"ROUTING",row,row.getPlantId(),plantMap,"PLANT_MISSING"));
        routingSteps.selectList(null).forEach(row->{required(out,"ROUTING_STEP",row,row.getRevisionId(),routingRevisionMap,"REVISION_MISSING");
            Map<String,? extends io.github.chrisshi.mom.data.entity.BaseIdEntity> resources="PRODUCTION_LINE".equals(row.getResourceType())?lineMap:stationMap;
            required(out,"ROUTING_STEP",row,row.getResourceId(),resources,"MANUFACTURING_RESOURCE_MISSING");});
        productionVersions.selectList(null).forEach(row->{required(out,"PRODUCTION_VERSION",row,row.getPlantId(),plantMap,"PLANT_MISSING");required(out,"PRODUCTION_VERSION",row,row.getOutputMaterialId(),materialMap,"OUTPUT_MATERIAL_MISSING");
            FormulaRevisionEntity formula=formulaRevisionMap.get(row.getFormulaRevisionId());RoutingRevisionEntity routing=routingRevisionMap.get(row.getRoutingRevisionId());
            if(formula==null||!"RELEASED".equals(formula.getStatus()))out.add("MANUFACTURING_REFERENCE","PRODUCTION_VERSION",row,"ERROR","FORMULA_REVISION_NOT_RELEASED",row.getFormulaRevisionId());
            if(routing==null||!"RELEASED".equals(routing.getStatus()))out.add("MANUFACTURING_REFERENCE","PRODUCTION_VERSION",row,"ERROR","ROUTING_REVISION_NOT_RELEASED",row.getRoutingRevisionId());});
    }

    private void quality(Collector out) {
        Map<String,PlantEntity> plantMap=map(plants.selectList(null));
        Map<String,MaterialEntity> materialMap=map(materials.selectList(null));
        Map<String,UomEntity> uomMap=map(uoms.selectList(null));
        Map<String,InspectionCharacteristicEntity> characteristicMap=map(inspectionCharacteristics.selectList(null));
        Map<String,InspectionMethodEntity> methodMap=map(inspectionMethods.selectList(null));
        Map<String,QualitySpecificationEntity> specificationMap=map(qualitySpecifications.selectList(null));
        Map<String,SpecificationRevisionEntity> revisionMap=map(specificationRevisions.selectList(null));
        characteristicMap.values().forEach(row->required(out,"INSPECTION_CHARACTERISTIC",row,
                row.getDefaultUomId(),uomMap,"DEFAULT_UOM_MISSING"));
        specificationMap.values().forEach(row->{
            required(out,"QUALITY_SPECIFICATION",row,row.getPlantId(),plantMap,"PLANT_MISSING");
            required(out,"QUALITY_SPECIFICATION",row,row.getMaterialId(),materialMap,"MATERIAL_MISSING");
        });
        revisionMap.values().forEach(row->required(out,"SPECIFICATION_REVISION",row,
                row.getSpecificationId(),specificationMap,"SPECIFICATION_MISSING"));
        specificationItems.selectList(null).forEach(row->{
            required(out,"SPECIFICATION_ITEM",row,row.getRevisionId(),revisionMap,"REVISION_MISSING");
            required(out,"SPECIFICATION_ITEM",row,row.getCharacteristicId(),characteristicMap,"CHARACTERISTIC_MISSING");
            required(out,"SPECIFICATION_ITEM",row,row.getMethodId(),methodMap,"METHOD_MISSING");
            required(out,"SPECIFICATION_ITEM",row,row.getUomId(),uomMap,"UOM_MISSING");
        });
    }

    private void parent(Collector out,String type,BaseIdEntity row,String parentId,Map<String,? extends BaseIdEntity> parents,String parentType){BaseIdEntity parent=parents.get(parentId);if(parent==null)out.add("PARENT_CHAIN",type,row,"ERROR",parentType+"_MISSING",parentId);else if(!enabled(parent))out.add("PARENT_CHAIN",type,row,"ERROR",parentType+"_DISABLED",parentId);}
    private void required(Collector out,String type,BaseIdEntity row,String id,Map<String,? extends BaseIdEntity> values,String reason){if(!values.containsKey(id))out.add("REFERENCE",type,row,"ERROR",reason,id);}
    private boolean enabled(Object row){
        if(row==null)return false;
        return switch(row){
            case PlantEntity e -> ENABLED.equals(e.getStatus()); case WorkshopEntity e -> ENABLED.equals(e.getStatus());
            case ProductionLineEntity e -> ENABLED.equals(e.getStatus()); case WorkstationEntity e -> ENABLED.equals(e.getStatus());
            case WarehouseEntity e -> ENABLED.equals(e.getStatus()); case WarehouseAreaEntity e -> ENABLED.equals(e.getStatus());
            case LocationEntity e -> ENABLED.equals(e.getStatus()); case LocationTypeEntity e -> ENABLED.equals(e.getStatus());
            case MaterialCategoryEntity e -> ENABLED.equals(e.getStatus()); case MaterialEntity e -> ENABLED.equals(e.getStatus());
            case DimensionEntity e -> ENABLED.equals(e.getStatus()); case UomCategoryEntity e -> ENABLED.equals(e.getStatus());
            case UomEntity e -> ENABLED.equals(e.getStatus()); default -> true;
        };
    }
    private static <T extends BaseIdEntity> Map<String,T> map(List<T> values){return values.stream().collect(Collectors.toMap(BaseIdEntity::getId,Function.identity()));}
    private static String code(Object row){return switch(row){
        case PlantEntity e->e.getCode();case WorkshopEntity e->e.getCode();case ProductionLineEntity e->e.getCode();case WorkstationEntity e->e.getCode();
        case WarehouseEntity e->e.getCode();case WarehouseAreaEntity e->e.getCode();case LocationEntity e->e.getCode();case LocationTypeEntity e->e.getCode();
        case MaterialCategoryEntity e->e.getCode();case MaterialEntity e->e.getCode();case DimensionEntity e->e.getCode();case UomCategoryEntity e->e.getCode();case UomEntity e->e.getCode();
        case FormulaEntity e->e.getCode();case RoutingEntity e->e.getCode();case ProductionVersionEntity e->e.getCode();
        case InspectionCharacteristicEntity e->e.getCode();case InspectionMethodEntity e->e.getCode();
        case QualitySpecificationEntity e->e.getCode();default->null;};}
    private static final class Collector {private final List<Issue> issues=new ArrayList<>();private boolean truncated;
        private void add(String issueType,String resourceType,BaseIdEntity row,String severity,String reason,String related){if(issues.size()>=ISSUE_LIMIT){truncated=true;return;}issues.add(new Issue(issueType,resourceType,row.getId(),code(row),severity,reason,related));}}
}
