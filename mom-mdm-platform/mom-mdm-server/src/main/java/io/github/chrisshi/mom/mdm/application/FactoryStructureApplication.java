package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.api.ManufacturingResourceReference;
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

import java.time.ZoneId;

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
    public PlantView createPlant(String code, String name, String timeZoneId, String status) {
        PlantEntity entity = new PlantEntity();
        entity.setCode(MdmMasterDataRules.code(code));
        setName(entity, name);
        entity.setTimeZoneId(requireIanaTimeZone(timeZoneId));
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> plantMapper.insert(entity), "Plant");
        return toView(entity);
    }

    /** 更新 Plant 名称并可补齐或调整权威业务时区；请求不接收 Code。 */
    @Transactional
    public PlantView updatePlant(String id, String name, String timeZoneId, Long version) {
        PlantEntity entity = requirePlant(id);
        requireVersion(entity.getVersion(), version);
        setName(entity, name);
        if (timeZoneId != null) entity.setTimeZoneId(requireIanaTimeZone(timeZoneId));
        requireUpdated(plantMapper.updateById(entity), () -> plantMapper.selectById(entity.getId()), "Plant");
        return toView(entity);
    }

    /** 按 ID 返回 Plant 详情。 */
    @Transactional(readOnly = true)
    public PlantView getPlant(String id) {
        return toView(requirePlant(id));
    }

    /** 沿父链读取当前资源快照；停用仅拒绝新引用，不改写子资源或历史业务。 */
    @Transactional(readOnly = true)
    public ManufacturingResourceReference getManufacturingResourceReference(String resourceType, String id) {
        return switch (resourceType) {
            case "PLANT" -> {
                PlantEntity plant = requirePlant(id);
                yield resourceReference("PLANT", plant.getId(), plant.getCode(), plant.getName(),
                        null, plant, plant.getVersion(), plantReason(plant));
            }
            case "WORKSHOP" -> {
                WorkshopEntity workshop = requireWorkshop(id);
                PlantEntity plant = plantMapper.selectById(workshop.getPlantId());
                String reason = plant == null ? "PLANT_MISSING" : firstReason(plantReason(plant),
                        disabledReason(workshop.getStatus(), "WORKSHOP"));
                yield resourceReference("WORKSHOP", workshop.getId(), workshop.getCode(), workshop.getName(),
                        workshop.getPlantId(), plant, workshop.getVersion(), reason);
            }
            case "PRODUCTION_LINE" -> {
                ProductionLineEntity line = requireProductionLine(id);
                WorkshopEntity workshop = workshopMapper.selectById(line.getWorkshopId());
                PlantEntity plant = workshop == null ? null : plantMapper.selectById(workshop.getPlantId());
                String reason = workshop == null ? "WORKSHOP_MISSING" : plant == null ? "PLANT_MISSING"
                        : firstReason(plantReason(plant), disabledReason(workshop.getStatus(), "WORKSHOP"),
                                disabledReason(line.getStatus(), "PRODUCTION_LINE"));
                yield resourceReference("PRODUCTION_LINE", line.getId(), line.getCode(), line.getName(),
                        line.getWorkshopId(), plant, line.getVersion(), reason);
            }
            case "WORKSTATION" -> {
                WorkstationEntity station = requireWorkstation(id);
                ProductionLineEntity line = productionLineMapper.selectById(station.getProductionLineId());
                WorkshopEntity workshop = line == null ? null : workshopMapper.selectById(line.getWorkshopId());
                PlantEntity plant = workshop == null ? null : plantMapper.selectById(workshop.getPlantId());
                String reason = line == null ? "PRODUCTION_LINE_MISSING" : workshop == null ? "WORKSHOP_MISSING"
                        : plant == null ? "PLANT_MISSING"
                        : firstReason(plantReason(plant), disabledReason(workshop.getStatus(), "WORKSHOP"),
                                disabledReason(line.getStatus(), "PRODUCTION_LINE"),
                                disabledReason(station.getStatus(), "WORKSTATION"));
                yield resourceReference("WORKSTATION", station.getId(), station.getCode(), station.getName(),
                        station.getProductionLineId(), plant, station.getVersion(), reason);
            }
            default -> throw new MdmException(MdmException.Kind.BAD_REQUEST,
                    "mdm.validation_failed", "mdm.error.validation_failed");
        };
    }

    private static ManufacturingResourceReference resourceReference(
            String type, String id, String code, String name, String parentId,
            PlantEntity plant, Long version, String reason) {
        return new ManufacturingResourceReference(type, id, code, name, parentId,
                plant == null ? null : plant.getId(), plant == null ? null : plant.getTimeZoneId(),
                null, version, reason == null, reason);
    }

    private static String plantReason(PlantEntity plant) {
        if (!MdmMasterDataRules.ENABLED.equals(plant.getStatus())) return "PLANT_DISABLED";
        if (plant.getTimeZoneId() == null) return "PLANT_TIME_ZONE_MISSING";
        return null;
    }

    private static String disabledReason(String status, String type) {
        return MdmMasterDataRules.ENABLED.equals(status) ? null : type + "_DISABLED";
    }

    private static String firstReason(String... reasons) {
        for (String reason : reasons) if (reason != null) return reason;
        return null;
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
        PlantEntity plant = lockUsablePlant(plantId);
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
        WorkshopEntity workshop = lockUsableWorkshopChain(workshopId);
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
        ProductionLineEntity productionLine = lockUsableProductionLineChain(productionLineId);
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
        requireUpdated(workstationMapper.updateById(entity), () -> workstationMapper.selectById(id),
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
        WorkshopEntity entity = MdmMasterDataRules.ENABLED.equals(status)
                ? lockWorkshopAfterPlant(id) : requireWorkshopForUpdate(id);
        requireVersion(entity.getVersion(), version);
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(workshopMapper.updateById(entity), () -> workshopMapper.selectById(entity.getId()), "Workshop");
        return toView(entity);
    }

    private ProductionLineView changeProductionLineStatus(String id, String status, Long version) {
        ProductionLineEntity entity = MdmMasterDataRules.ENABLED.equals(status)
                ? lockProductionLineAfterAncestors(id) : requireProductionLineForUpdate(id);
        requireVersion(entity.getVersion(), version);
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(productionLineMapper.updateById(entity),
                () -> productionLineMapper.selectById(entity.getId()), "ProductionLine");
        return toView(entity);
    }

    private WorkstationView changeWorkstationStatus(String id, String status, Long version) {
        WorkstationEntity entity = requireWorkstation(id);
        if (MdmMasterDataRules.ENABLED.equals(status)) {
            lockUsableProductionLineChain(entity.getProductionLineId());
            entity = requireWorkstation(id);
        }
        requireVersion(entity.getVersion(), version);
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(workstationMapper.updateById(entity), () -> workstationMapper.selectById(id),
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

    /** 统一按 Plant → Workshop → ProductionLine 锁顺序，阻止祖先停用与子级新引用交叉提交。 */
    private PlantEntity lockUsablePlant(String id) {
        PlantEntity plant = requirePlantForUpdate(id);
        MdmMasterDataRules.requireEnabled(plant.getStatus(), "Plant");
        if (plant.getTimeZoneId() == null) throw MdmException.invalidReference();
        return plant;
    }

    private WorkshopEntity lockUsableWorkshopChain(String id) {
        WorkshopEntity snapshot = requireWorkshop(id);
        PlantEntity plant = lockUsablePlant(snapshot.getPlantId());
        WorkshopEntity workshop = requireWorkshopForUpdate(id);
        if (!plant.getId().equals(workshop.getPlantId())) throw MdmException.invalidReference();
        MdmMasterDataRules.requireEnabled(workshop.getStatus(), "Workshop");
        return workshop;
    }

    private ProductionLineEntity lockUsableProductionLineChain(String id) {
        ProductionLineEntity snapshot = requireProductionLine(id);
        WorkshopEntity workshop = lockUsableWorkshopChain(snapshot.getWorkshopId());
        ProductionLineEntity line = requireProductionLineForUpdate(id);
        if (!workshop.getId().equals(line.getWorkshopId())) throw MdmException.invalidReference();
        MdmMasterDataRules.requireEnabled(line.getStatus(), "ProductionLine");
        return line;
    }

    private WorkshopEntity lockWorkshopAfterPlant(String id) {
        WorkshopEntity snapshot = requireWorkshop(id);
        PlantEntity plant = lockUsablePlant(snapshot.getPlantId());
        WorkshopEntity workshop = requireWorkshopForUpdate(id);
        if (!plant.getId().equals(workshop.getPlantId())) throw MdmException.invalidReference();
        return workshop;
    }

    private ProductionLineEntity lockProductionLineAfterAncestors(String id) {
        ProductionLineEntity snapshot = requireProductionLine(id);
        WorkshopEntity workshop = lockUsableWorkshopChain(snapshot.getWorkshopId());
        ProductionLineEntity line = requireProductionLineForUpdate(id);
        if (!workshop.getId().equals(line.getWorkshopId())) throw MdmException.invalidReference();
        return line;
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

    private static PlantView toView(PlantEntity e) { return new PlantView(e.getId(), e.getCode(), e.getName(), e.getTimeZoneId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }

    /** IANA TZDB 标识白名单拒绝固定 Offset 与 GMT+8 一类会丢失 DST 规则的输入。 */
    private static String requireIanaTimeZone(String value) {
        if (value == null || value.isBlank() || value.length() > 64
                || !ZoneId.getAvailableZoneIds().contains(value)) {
            throw new MdmException(MdmException.Kind.BAD_REQUEST,
                    "mdm.validation_failed", "mdm.error.validation_failed");
        }
        return value;
    }
    private static WorkshopView toView(WorkshopEntity e) { return new WorkshopView(e.getId(), e.getCode(), e.getName(), e.getPlantId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    private static ProductionLineView toView(ProductionLineEntity e) { return new ProductionLineView(e.getId(), e.getCode(), e.getName(), e.getWorkshopId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    private static WorkstationView toView(WorkstationEntity e) { return new WorkstationView(e.getId(), e.getCode(), e.getName(), e.getProductionLineId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }

    @FunctionalInterface private interface IntOperation { int execute(); }
    @FunctionalInterface private interface EntityLookup { Object find(); }
}
