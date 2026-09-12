package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.PlantView;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.ProductionLineView;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.WorkshopView;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.WorkstationView;
import io.github.chrisshi.mom.mdm.infrastructure.entity.PlantEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.ProductionLineEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WorkshopEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WorkstationEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.PlantMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.ProductionLineMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.WorkshopMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.WorkstationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plant、Workshop、ProductionLine、Workstation 的 Level 1 用例与本地事务边界。
 *
 * <p>调用方向为 Controller → Application → Mapper。父级存在性在创建时显式校验，唯一性由 PostgreSQL
 * 约束最终保证，更新由 Version 乐观锁保证；不使用物理外键、级联停用、Redis、MQ 或跨服务调用。</p>
 */
@Component
public class FactoryStructureApplication {
    private final PlantMapper plantMapper;
    private final WorkshopMapper workshopMapper;
    private final ProductionLineMapper productionLineMapper;
    private final WorkstationMapper workstationMapper;

    /** 注入四类主数据的单表 Mapper。 */
    public FactoryStructureApplication(PlantMapper plantMapper, WorkshopMapper workshopMapper,
                                       ProductionLineMapper productionLineMapper, WorkstationMapper workstationMapper) {
        this.plantMapper = plantMapper;
        this.workshopMapper = workshopMapper;
        this.productionLineMapper = productionLineMapper;
        this.workstationMapper = workstationMapper;
    }

    /** 创建平台编码唯一的 Plant；重复请求不会视为幂等成功。 */
    @Transactional
    public PlantView createPlant(String code, String nameZh, String nameEn, String status) {
        PlantEntity entity = new PlantEntity();
        entity.setCode(MdmMasterDataRules.code(code));
        setNames(entity, nameZh, nameEn);
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> plantMapper.insert(entity), "Plant");
        return toView(entity);
    }

    /** 更新 Plant 名称；请求不接收 Code，因此不会改变业务身份。 */
    @Transactional
    public PlantView updatePlant(String id, String nameZh, String nameEn, Long version) {
        PlantEntity entity = requirePlant(id);
        requireVersion(entity.getVersion(), version);
        setNames(entity, nameZh, nameEn);
        requireUpdated(plantMapper.updateById(entity), () -> plantMapper.selectById(entity.getId()), "Plant");
        return toView(entity);
    }

    /** 按 ID 返回 Plant 详情。 */
    @Transactional(readOnly = true)
    public PlantView getPlant(String id) {
        return toView(requirePlant(id));
    }

    /** 按 Code、ID 稳定排序分页查询 Plant，并复用 PageAdapter 转换分页元数据。 */
    @Transactional(readOnly = true)
    public PageResult<PlantView> pagePlants(long pageNo, long pageSize) {
        Page<PlantEntity> page = PageAdapter.toPage(new PageQuery<>(null, pageNo, pageSize));
        plantMapper.selectPage(page, new LambdaQueryWrapper<PlantEntity>()
                .orderByAsc(PlantEntity::getCode).orderByAsc(PlantEntity::getId));
        return PageAdapter.toResult(page, FactoryStructureApplication::toView);
    }

    /** 显式启用 Plant；不级联修改任何子级状态。 */
    @Transactional
    public PlantView enablePlant(String id, Long version) {
        return changePlantStatus(id, MdmMasterDataRules.ENABLED, version);
    }

    /** 显式停用 Plant；不级联修改任何子级状态。 */
    @Transactional
    public PlantView disablePlant(String id, Long version) {
        return changePlantStatus(id, MdmMasterDataRules.DISABLED, version);
    }

    /** 在已启用 Plant 下创建 Workshop，同一 Plant 内 Code 唯一。 */
    @Transactional
    public WorkshopView createWorkshop(String plantId, String code, String nameZh, String nameEn, String status) {
        PlantEntity plant = requirePlant(plantId);
        MdmMasterDataRules.requireEnabled(plant.getStatus(), "Plant");
        WorkshopEntity entity = new WorkshopEntity();
        entity.setPlantId(plant.getId());
        entity.setCode(MdmMasterDataRules.code(code));
        setNames(entity, nameZh, nameEn);
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> workshopMapper.insert(entity), "Workshop");
        return toView(entity);
    }

    /** 更新 Workshop 名称，不允许改变 Plant 或 Code。 */
    @Transactional
    public WorkshopView updateWorkshop(String id, String nameZh, String nameEn, Long version) {
        WorkshopEntity entity = requireWorkshop(id);
        requireVersion(entity.getVersion(), version);
        setNames(entity, nameZh, nameEn);
        requireUpdated(workshopMapper.updateById(entity), () -> workshopMapper.selectById(entity.getId()), "Workshop");
        return toView(entity);
    }

    /** 按 ID 返回 Workshop 详情。 */
    @Transactional(readOnly = true)
    public WorkshopView getWorkshop(String id) {
        return toView(requireWorkshop(id));
    }

    /** 可按 Plant 过滤并稳定排序分页查询 Workshop。 */
    @Transactional(readOnly = true)
    public PageResult<WorkshopView> pageWorkshops(String plantId, long pageNo, long pageSize) {
        Page<WorkshopEntity> page = PageAdapter.toPage(new PageQuery<>(plantId, pageNo, pageSize));
        LambdaQueryWrapper<WorkshopEntity> query = new LambdaQueryWrapper<>();
        if (plantId != null && !plantId.isBlank()) {
            query.eq(WorkshopEntity::getPlantId, MdmMasterDataRules.id(plantId, "plantId"));
        }
        query.orderByAsc(WorkshopEntity::getCode).orderByAsc(WorkshopEntity::getId);
        workshopMapper.selectPage(page, query);
        return PageAdapter.toResult(page, FactoryStructureApplication::toView);
    }

    /** 启用 Workshop，不隐式改变 Plant 或子级状态。 */
    @Transactional
    public WorkshopView enableWorkshop(String id, Long version) {
        return changeWorkshopStatus(id, MdmMasterDataRules.ENABLED, version);
    }

    /** 停用 Workshop，不级联停用 ProductionLine。 */
    @Transactional
    public WorkshopView disableWorkshop(String id, Long version) {
        return changeWorkshopStatus(id, MdmMasterDataRules.DISABLED, version);
    }

    /** 在已启用 Workshop 下创建 ProductionLine，同一 Workshop 内 Code 唯一。 */
    @Transactional
    public ProductionLineView createProductionLine(String workshopId, String code, String nameZh,
                                                   String nameEn, String status) {
        WorkshopEntity workshop = requireWorkshop(workshopId);
        MdmMasterDataRules.requireEnabled(workshop.getStatus(), "Workshop");
        ProductionLineEntity entity = new ProductionLineEntity();
        entity.setWorkshopId(workshop.getId());
        entity.setCode(MdmMasterDataRules.code(code));
        setNames(entity, nameZh, nameEn);
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> productionLineMapper.insert(entity), "ProductionLine");
        return toView(entity);
    }

    /** 更新 ProductionLine 名称，不允许改变 Workshop 或 Code。 */
    @Transactional
    public ProductionLineView updateProductionLine(String id, String nameZh, String nameEn, Long version) {
        ProductionLineEntity entity = requireProductionLine(id);
        requireVersion(entity.getVersion(), version);
        setNames(entity, nameZh, nameEn);
        requireUpdated(productionLineMapper.updateById(entity),
                () -> productionLineMapper.selectById(entity.getId()), "ProductionLine");
        return toView(entity);
    }

    /** 按 ID 返回 ProductionLine 详情。 */
    @Transactional(readOnly = true)
    public ProductionLineView getProductionLine(String id) {
        return toView(requireProductionLine(id));
    }

    /** 可按 Workshop 过滤并稳定排序分页查询 ProductionLine。 */
    @Transactional(readOnly = true)
    public PageResult<ProductionLineView> pageProductionLines(String workshopId, long pageNo, long pageSize) {
        Page<ProductionLineEntity> page = PageAdapter.toPage(new PageQuery<>(workshopId, pageNo, pageSize));
        LambdaQueryWrapper<ProductionLineEntity> query = new LambdaQueryWrapper<>();
        if (workshopId != null && !workshopId.isBlank()) {
            query.eq(ProductionLineEntity::getWorkshopId, MdmMasterDataRules.id(workshopId, "workshopId"));
        }
        query.orderByAsc(ProductionLineEntity::getCode).orderByAsc(ProductionLineEntity::getId);
        productionLineMapper.selectPage(page, query);
        return PageAdapter.toResult(page, FactoryStructureApplication::toView);
    }

    /** 启用 ProductionLine，不级联修改 Workstation。 */
    @Transactional
    public ProductionLineView enableProductionLine(String id, Long version) {
        return changeProductionLineStatus(id, MdmMasterDataRules.ENABLED, version);
    }

    /** 停用 ProductionLine，不级联修改 Workstation。 */
    @Transactional
    public ProductionLineView disableProductionLine(String id, Long version) {
        return changeProductionLineStatus(id, MdmMasterDataRules.DISABLED, version);
    }

    /** 在已启用 ProductionLine 下创建 Workstation，同一 ProductionLine 内 Code 唯一。 */
    @Transactional
    public WorkstationView createWorkstation(String productionLineId, String code, String nameZh,
                                             String nameEn, String status) {
        ProductionLineEntity productionLine = requireProductionLine(productionLineId);
        MdmMasterDataRules.requireEnabled(productionLine.getStatus(), "ProductionLine");
        WorkstationEntity entity = new WorkstationEntity();
        entity.setProductionLineId(productionLine.getId());
        entity.setCode(MdmMasterDataRules.code(code));
        setNames(entity, nameZh, nameEn);
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> workstationMapper.insert(entity), "Workstation");
        return toView(entity);
    }

    /** 更新 Workstation 名称，不允许改变 ProductionLine 或 Code。 */
    @Transactional
    public WorkstationView updateWorkstation(String id, String nameZh, String nameEn, Long version) {
        WorkstationEntity entity = requireWorkstation(id);
        requireVersion(entity.getVersion(), version);
        setNames(entity, nameZh, nameEn);
        requireUpdated(workstationMapper.updateById(entity), () -> workstationMapper.selectById(entity.getId()),
                "Workstation");
        return toView(entity);
    }

    /** 按 ID 返回 Workstation 详情。 */
    @Transactional(readOnly = true)
    public WorkstationView getWorkstation(String id) {
        return toView(requireWorkstation(id));
    }

    /** 可按 ProductionLine 过滤并稳定排序分页查询 Workstation。 */
    @Transactional(readOnly = true)
    public PageResult<WorkstationView> pageWorkstations(String productionLineId, long pageNo, long pageSize) {
        Page<WorkstationEntity> page = PageAdapter.toPage(new PageQuery<>(productionLineId, pageNo, pageSize));
        LambdaQueryWrapper<WorkstationEntity> query = new LambdaQueryWrapper<>();
        if (productionLineId != null && !productionLineId.isBlank()) {
            query.eq(WorkstationEntity::getProductionLineId,
                    MdmMasterDataRules.id(productionLineId, "productionLineId"));
        }
        query.orderByAsc(WorkstationEntity::getCode).orderByAsc(WorkstationEntity::getId);
        workstationMapper.selectPage(page, query);
        return PageAdapter.toResult(page, FactoryStructureApplication::toView);
    }

    /** 启用 Workstation。 */
    @Transactional
    public WorkstationView enableWorkstation(String id, Long version) {
        return changeWorkstationStatus(id, MdmMasterDataRules.ENABLED, version);
    }

    /** 停用 Workstation。 */
    @Transactional
    public WorkstationView disableWorkstation(String id, Long version) {
        return changeWorkstationStatus(id, MdmMasterDataRules.DISABLED, version);
    }

    private PlantView changePlantStatus(String id, String status, Long version) {
        PlantEntity entity = requirePlant(id);
        requireVersion(entity.getVersion(), version);
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(plantMapper.updateById(entity), () -> plantMapper.selectById(entity.getId()), "Plant");
        return toView(entity);
    }

    private WorkshopView changeWorkshopStatus(String id, String status, Long version) {
        WorkshopEntity entity = requireWorkshop(id);
        requireVersion(entity.getVersion(), version);
        if (MdmMasterDataRules.ENABLED.equals(status)) {
            MdmMasterDataRules.requireEnabled(requirePlant(entity.getPlantId()).getStatus(), "Plant");
        }
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(workshopMapper.updateById(entity), () -> workshopMapper.selectById(entity.getId()), "Workshop");
        return toView(entity);
    }

    private ProductionLineView changeProductionLineStatus(String id, String status, Long version) {
        ProductionLineEntity entity = requireProductionLine(id);
        requireVersion(entity.getVersion(), version);
        if (MdmMasterDataRules.ENABLED.equals(status)) {
            MdmMasterDataRules.requireEnabled(requireWorkshop(entity.getWorkshopId()).getStatus(), "Workshop");
        }
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(productionLineMapper.updateById(entity),
                () -> productionLineMapper.selectById(entity.getId()), "ProductionLine");
        return toView(entity);
    }

    private WorkstationView changeWorkstationStatus(String id, String status, Long version) {
        WorkstationEntity entity = requireWorkstation(id);
        requireVersion(entity.getVersion(), version);
        if (MdmMasterDataRules.ENABLED.equals(status)) {
            MdmMasterDataRules.requireEnabled(
                    requireProductionLine(entity.getProductionLineId()).getStatus(), "ProductionLine");
        }
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(workstationMapper.updateById(entity), () -> workstationMapper.selectById(entity.getId()),
                "Workstation");
        return toView(entity);
    }

    private PlantEntity requirePlant(String id) {
        PlantEntity entity = plantMapper.selectById(MdmMasterDataRules.id(id, "plantId"));
        if (entity == null) throw MdmException.notFound("Plant");
        return entity;
    }

    private WorkshopEntity requireWorkshop(String id) {
        WorkshopEntity entity = workshopMapper.selectById(MdmMasterDataRules.id(id, "workshopId"));
        if (entity == null) throw MdmException.notFound("Workshop");
        return entity;
    }

    private ProductionLineEntity requireProductionLine(String id) {
        ProductionLineEntity entity = productionLineMapper.selectById(MdmMasterDataRules.id(id, "productionLineId"));
        if (entity == null) throw MdmException.notFound("ProductionLine");
        return entity;
    }

    private WorkstationEntity requireWorkstation(String id) {
        WorkstationEntity entity = workstationMapper.selectById(MdmMasterDataRules.id(id, "workstationId"));
        if (entity == null) throw MdmException.notFound("Workstation");
        return entity;
    }

    private static void requireVersion(Long actual, Long expected) {
        long validated = MdmMasterDataRules.version(expected);
        if (actual == null || actual != validated) throw MdmException.versionConflict();
    }

    private static void insert(IntOperation operation, String resourceName) {
        try {
            operation.execute();
        } catch (DuplicateKeyException exception) {
            throw MdmException.codeConflict(resourceName);
        }
    }

    private static void requireUpdated(int affected, EntityLookup lookup, String resourceName) {
        if (affected == 1) return;
        if (lookup.find() == null) throw MdmException.notFound(resourceName);
        throw MdmException.versionConflict();
    }

    private static void setNames(PlantEntity e, String zh, String en) { e.setNameZh(MdmMasterDataRules.nameZh(zh)); e.setNameEn(MdmMasterDataRules.nameEn(en)); }
    private static void setNames(WorkshopEntity e, String zh, String en) { e.setNameZh(MdmMasterDataRules.nameZh(zh)); e.setNameEn(MdmMasterDataRules.nameEn(en)); }
    private static void setNames(ProductionLineEntity e, String zh, String en) { e.setNameZh(MdmMasterDataRules.nameZh(zh)); e.setNameEn(MdmMasterDataRules.nameEn(en)); }
    private static void setNames(WorkstationEntity e, String zh, String en) { e.setNameZh(MdmMasterDataRules.nameZh(zh)); e.setNameEn(MdmMasterDataRules.nameEn(en)); }

    private static PlantView toView(PlantEntity e) { return new PlantView(e.getId(), e.getCode(), e.getNameZh(), e.getNameEn(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    private static WorkshopView toView(WorkshopEntity e) { return new WorkshopView(e.getId(), e.getCode(), e.getNameZh(), e.getNameEn(), e.getPlantId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    private static ProductionLineView toView(ProductionLineEntity e) { return new ProductionLineView(e.getId(), e.getCode(), e.getNameZh(), e.getNameEn(), e.getWorkshopId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    private static WorkstationView toView(WorkstationEntity e) { return new WorkstationView(e.getId(), e.getCode(), e.getNameZh(), e.getNameEn(), e.getProductionLineId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }

    @FunctionalInterface private interface IntOperation { int execute(); }
    @FunctionalInterface private interface EntityLookup { Object find(); }
}
