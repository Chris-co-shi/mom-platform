package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.PlantPageParams;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.ProductionLinePageParams;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.WorkshopPageParams;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.WorkstationPageParams;
import io.github.chrisshi.mom.mdm.application.model.MdmMasterDataViews.PlantView;
import io.github.chrisshi.mom.mdm.application.model.MdmMasterDataViews.ProductionLineView;
import io.github.chrisshi.mom.mdm.application.model.MdmMasterDataViews.WorkshopView;
import io.github.chrisshi.mom.mdm.application.model.MdmMasterDataViews.WorkstationView;
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

import static io.github.chrisshi.mom.mdm.application.MdmMasterDataRules.requireVersion;

/**
 * Plant、Workshop、ProductionLine、Workstation 的 Level 1 用例与本地事务边界。
 *
 * <p>调用方向为 Controller → Application → Mapper。创建、启用直接子级与父级停用通过父行锁串行，
 * 唯一性由 PostgreSQL 约束最终保证，目标行更新由 Version 乐观锁保证；不使用物理外键、级联停用、
 * Redis、MQ 或跨服务调用，数据库不可用或锁等待失败时本地事务整体回滚。</p>
 */
@Component
public class FactoryStructureApplication {
    private final PlantMapper plantMapper;
    private final WorkshopMapper workshopMapper;
    private final ProductionLineMapper productionLineMapper;
    private final WorkstationMapper workstationMapper;
    private final PageAdapter pageAdapter;

    /**
     * 注入四类主数据的单表 Mapper 与统一分页适配器。
     *
     * @param plantMapper Plant 单表 Mapper
     * @param workshopMapper Workshop 单表 Mapper
     * @param productionLineMapper ProductionLine 单表 Mapper
     * @param workstationMapper Workstation 单表 Mapper
     * @param pageAdapter 配置化分页适配器
     */
    public FactoryStructureApplication(PlantMapper plantMapper, WorkshopMapper workshopMapper,
                                       ProductionLineMapper productionLineMapper, WorkstationMapper workstationMapper,
                                       PageAdapter pageAdapter) {
        this.plantMapper = plantMapper;
        this.workshopMapper = workshopMapper;
        this.productionLineMapper = productionLineMapper;
        this.workstationMapper = workstationMapper;
        this.pageAdapter = pageAdapter;
    }

    /** 创建平台编码唯一的 Plant；重复请求不会视为幂等成功。 */
    @Transactional
    public PlantView createPlant(String code, String name, String status) {
        PlantEntity entity = new PlantEntity();
        entity.setCode(MdmMasterDataRules.code(code));
        setName(entity, name);
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> plantMapper.insert(entity), "Plant");
        return toView(entity);
    }

    /** 更新 Plant 名称；请求不接收 Code，因此不会改变业务身份。 */
    @Transactional
    public PlantView updatePlant(String id, String name, Long version) {
        PlantEntity entity = requirePlant(id);
        requireVersion(entity.getVersion(), version);
        setName(entity, name);
        requireUpdated(plantMapper.updateById(entity), () -> plantMapper.selectById(entity.getId()), "Plant");
        return toView(entity);
    }

    /** 按 ID 返回 Plant 详情。 */
    @Transactional(readOnly = true)
    public PlantView getPlant(String id) {
        return toView(requirePlant(id));
    }

    /**
     * 按 Code、ID 稳定排序分页查询 Plant。
     *
     * @param pageQuery 包含空 Plant Params 和分页信息的唯一业务入参
     * @return 统一分页结果；只读、幂等且无持久化副作用
     */
    @Transactional(readOnly = true)
    public PageResult<PlantView> pagePlants(PageQuery<PlantPageParams> pageQuery) {
        Page<PlantEntity> page = pageAdapter.toPage(pageQuery);
        plantMapper.selectPage(page, new LambdaQueryWrapper<PlantEntity>()
                .orderByAsc(PlantEntity::getCode).orderByAsc(PlantEntity::getId));
        return pageAdapter.toResult(page, FactoryStructureApplication::toView);
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
    public WorkshopView createWorkshop(String plantId, String code, String name, String status) {
        PlantEntity plant = requirePlantForUpdate(plantId);
        MdmMasterDataRules.requireEnabled(plant.getStatus(), "Plant");
        WorkshopEntity entity = new WorkshopEntity();
        entity.setPlantId(plant.getId());
        entity.setCode(MdmMasterDataRules.code(code));
        setName(entity, name);
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> workshopMapper.insert(entity), "Workshop");
        return toView(entity);
    }

    /** 更新 Workshop 名称，不允许改变 Plant 或 Code。 */
    @Transactional
    public WorkshopView updateWorkshop(String id, String name, Long version) {
        WorkshopEntity entity = requireWorkshop(id);
        requireVersion(entity.getVersion(), version);
        setName(entity, name);
        requireUpdated(workshopMapper.updateById(entity), () -> workshopMapper.selectById(entity.getId()), "Workshop");
        return toView(entity);
    }

    /** 按 ID 返回 Workshop 详情。 */
    @Transactional(readOnly = true)
    public WorkshopView getWorkshop(String id) {
        return toView(requireWorkshop(id));
    }

    /**
     * 可按 Plant 过滤并稳定排序分页查询 Workshop。
     *
     * @param pageQuery 包含可选 Plant ID 和分页信息的唯一业务入参
     * @return 统一分页结果；只读、幂等且无持久化副作用
     * @throws MdmException Plant ID 格式非法时抛出
     */
    @Transactional(readOnly = true)
    public PageResult<WorkshopView> pageWorkshops(PageQuery<WorkshopPageParams> pageQuery) {
        WorkshopPageParams params = pageQuery.params();
        Page<WorkshopEntity> page = pageAdapter.toPage(pageQuery);
        LambdaQueryWrapper<WorkshopEntity> query = new LambdaQueryWrapper<>();
        String plantId = params.plantId();
        if (plantId != null && !plantId.isBlank()) {
            query.eq(WorkshopEntity::getPlantId, MdmMasterDataRules.id(plantId, "plantId"));
        }
        query.orderByAsc(WorkshopEntity::getCode).orderByAsc(WorkshopEntity::getId);
        workshopMapper.selectPage(page, query);
        return pageAdapter.toResult(page, FactoryStructureApplication::toView);
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
    public ProductionLineView createProductionLine(String workshopId, String code, String name,
                                                   String status) {
        WorkshopEntity workshop = requireWorkshopForUpdate(workshopId);
        MdmMasterDataRules.requireEnabled(workshop.getStatus(), "Workshop");
        ProductionLineEntity entity = new ProductionLineEntity();
        entity.setWorkshopId(workshop.getId());
        entity.setCode(MdmMasterDataRules.code(code));
        setName(entity, name);
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> productionLineMapper.insert(entity), "ProductionLine");
        return toView(entity);
    }

    /** 更新 ProductionLine 名称，不允许改变 Workshop 或 Code。 */
    @Transactional
    public ProductionLineView updateProductionLine(String id, String name, Long version) {
        ProductionLineEntity entity = requireProductionLine(id);
        requireVersion(entity.getVersion(), version);
        setName(entity, name);
        requireUpdated(productionLineMapper.updateById(entity),
                () -> productionLineMapper.selectById(entity.getId()), "ProductionLine");
        return toView(entity);
    }

    /** 按 ID 返回 ProductionLine 详情。 */
    @Transactional(readOnly = true)
    public ProductionLineView getProductionLine(String id) {
        return toView(requireProductionLine(id));
    }

    /**
     * 可按 Workshop 过滤并稳定排序分页查询 ProductionLine。
     *
     * @param pageQuery 包含可选 Workshop ID 和分页信息的唯一业务入参
     * @return 统一分页结果；只读、幂等且无持久化副作用
     * @throws MdmException Workshop ID 格式非法时抛出
     */
    @Transactional(readOnly = true)
    public PageResult<ProductionLineView> pageProductionLines(PageQuery<ProductionLinePageParams> pageQuery) {
        ProductionLinePageParams params = pageQuery.params();
        Page<ProductionLineEntity> page = pageAdapter.toPage(pageQuery);
        LambdaQueryWrapper<ProductionLineEntity> query = new LambdaQueryWrapper<>();
        String workshopId = params.workshopId();
        if (workshopId != null && !workshopId.isBlank()) {
            query.eq(ProductionLineEntity::getWorkshopId, MdmMasterDataRules.id(workshopId, "workshopId"));
        }
        query.orderByAsc(ProductionLineEntity::getCode).orderByAsc(ProductionLineEntity::getId);
        productionLineMapper.selectPage(page, query);
        return pageAdapter.toResult(page, FactoryStructureApplication::toView);
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
    public WorkstationView createWorkstation(String productionLineId, String code, String name,
                                             String status) {
        ProductionLineEntity productionLine = requireProductionLineForUpdate(productionLineId);
        MdmMasterDataRules.requireEnabled(productionLine.getStatus(), "ProductionLine");
        WorkstationEntity entity = new WorkstationEntity();
        entity.setProductionLineId(productionLine.getId());
        entity.setCode(MdmMasterDataRules.code(code));
        setName(entity, name);
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> workstationMapper.insert(entity), "Workstation");
        return toView(entity);
    }

    /** 更新 Workstation 名称，不允许改变 ProductionLine 或 Code。 */
    @Transactional
    public WorkstationView updateWorkstation(String id, String name, Long version) {
        WorkstationEntity entity = requireWorkstation(id);
        requireVersion(entity.getVersion(), version);
        setName(entity, name);
        requireUpdated(workstationMapper.updateById(entity), () -> workstationMapper.selectById(entity.getId()),
                "Workstation");
        return toView(entity);
    }

    /** 按 ID 返回 Workstation 详情。 */
    @Transactional(readOnly = true)
    public WorkstationView getWorkstation(String id) {
        return toView(requireWorkstation(id));
    }

    /**
     * 可按 ProductionLine 过滤并稳定排序分页查询 Workstation。
     *
     * @param pageQuery 包含可选 ProductionLine ID 和分页信息的唯一业务入参
     * @return 统一分页结果；只读、幂等且无持久化副作用
     * @throws MdmException ProductionLine ID 格式非法时抛出
     */
    @Transactional(readOnly = true)
    public PageResult<WorkstationView> pageWorkstations(PageQuery<WorkstationPageParams> pageQuery) {
        WorkstationPageParams params = pageQuery.params();
        Page<WorkstationEntity> page = pageAdapter.toPage(pageQuery);
        LambdaQueryWrapper<WorkstationEntity> query = new LambdaQueryWrapper<>();
        String productionLineId = params.productionLineId();
        if (productionLineId != null && !productionLineId.isBlank()) {
            query.eq(WorkstationEntity::getProductionLineId,
                    MdmMasterDataRules.id(productionLineId, "productionLineId"));
        }
        query.orderByAsc(WorkstationEntity::getCode).orderByAsc(WorkstationEntity::getId);
        workstationMapper.selectPage(page, query);
        return pageAdapter.toResult(page, FactoryStructureApplication::toView);
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
        PlantEntity entity = requirePlantForUpdate(id);
        requireVersion(entity.getVersion(), version);
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(plantMapper.updateById(entity), () -> plantMapper.selectById(entity.getId()), "Plant");
        return toView(entity);
    }

    private WorkshopView changeWorkshopStatus(String id, String status, Long version) {
        WorkshopEntity entity = requireWorkshopForUpdate(id);
        requireVersion(entity.getVersion(), version);
        if (MdmMasterDataRules.ENABLED.equals(status)) {
            MdmMasterDataRules.requireEnabled(requirePlantForUpdate(entity.getPlantId()).getStatus(), "Plant");
        }
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(workshopMapper.updateById(entity), () -> workshopMapper.selectById(entity.getId()), "Workshop");
        return toView(entity);
    }

    private ProductionLineView changeProductionLineStatus(String id, String status, Long version) {
        ProductionLineEntity entity = requireProductionLineForUpdate(id);
        requireVersion(entity.getVersion(), version);
        if (MdmMasterDataRules.ENABLED.equals(status)) {
            MdmMasterDataRules.requireEnabled(
                    requireWorkshopForUpdate(entity.getWorkshopId()).getStatus(), "Workshop");
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
                    requireProductionLineForUpdate(entity.getProductionLineId()).getStatus(), "ProductionLine");
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

    /** 锁定 Plant，使其停用与直接子级创建、启用不能在检查后交叉提交。 */
    private PlantEntity requirePlantForUpdate(String id) {
        PlantEntity entity = plantMapper.selectByIdForUpdate(MdmMasterDataRules.id(id, "plantId"));
        if (entity == null) throw MdmException.notFound("Plant");
        return entity;
    }

    /** 锁定 Workshop，使其停用与 ProductionLine 创建、启用串行执行。 */
    private WorkshopEntity requireWorkshopForUpdate(String id) {
        WorkshopEntity entity = workshopMapper.selectByIdForUpdate(MdmMasterDataRules.id(id, "workshopId"));
        if (entity == null) throw MdmException.notFound("Workshop");
        return entity;
    }

    /** 锁定 ProductionLine，使其停用与 Workstation 创建、启用串行执行。 */
    private ProductionLineEntity requireProductionLineForUpdate(String id) {
        ProductionLineEntity entity = productionLineMapper.selectByIdForUpdate(
                MdmMasterDataRules.id(id, "productionLineId"));
        if (entity == null) throw MdmException.notFound("ProductionLine");
        return entity;
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

    private static void setName(PlantEntity e, String name) { e.setName(MdmMasterDataRules.name(name)); }
    private static void setName(WorkshopEntity e, String name) { e.setName(MdmMasterDataRules.name(name)); }
    private static void setName(ProductionLineEntity e, String name) { e.setName(MdmMasterDataRules.name(name)); }
    private static void setName(WorkstationEntity e, String name) { e.setName(MdmMasterDataRules.name(name)); }

    private static PlantView toView(PlantEntity e) { return new PlantView(e.getId(), e.getCode(), e.getName(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    private static WorkshopView toView(WorkshopEntity e) { return new WorkshopView(e.getId(), e.getCode(), e.getName(), e.getPlantId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    private static ProductionLineView toView(ProductionLineEntity e) { return new ProductionLineView(e.getId(), e.getCode(), e.getName(), e.getWorkshopId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    private static WorkstationView toView(WorkstationEntity e) { return new WorkstationView(e.getId(), e.getCode(), e.getName(), e.getProductionLineId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }

    @FunctionalInterface private interface IntOperation { int execute(); }
    @FunctionalInterface private interface EntityLookup { Object find(); }
}
