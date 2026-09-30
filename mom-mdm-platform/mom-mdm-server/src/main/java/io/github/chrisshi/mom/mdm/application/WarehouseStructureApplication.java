package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.application.MdmPageParams.WarehouseAreaPageParams;
import io.github.chrisshi.mom.mdm.application.MdmPageParams.WarehousePageParams;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.WarehouseAreaView;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.WarehouseView;
import io.github.chrisshi.mom.mdm.infrastructure.entity.PlantEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WarehouseAreaEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WarehouseEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.PlantMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.WarehouseAreaMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.WarehouseMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static io.github.chrisshi.mom.mdm.application.MdmMasterDataRules.requireVersion;

/**
 * Warehouse 与 WarehouseArea 的 Level 1 用例和本地事务边界。
 *
 * <p>仅管理静态主数据名称、父级和简单启停；不引入仓库类型、容量、库存策略或级联停用。数据库故障
 * 直接失败，唯一约束和 Version 分别作为并发创建与更新的最终防线。</p>
 */
@Component
public class WarehouseStructureApplication {
    private final PlantMapper plantMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseAreaMapper warehouseAreaMapper;
    private final PageAdapter pageAdapter;

    /**
     * 注入 Plant 引用校验、仓库结构 Mapper 与统一分页适配器。
     *
     * @param plantMapper Plant 单表 Mapper
     * @param warehouseMapper Warehouse 单表 Mapper
     * @param warehouseAreaMapper WarehouseArea 单表 Mapper
     * @param pageAdapter 配置化分页适配器
     */
    public WarehouseStructureApplication(PlantMapper plantMapper, WarehouseMapper warehouseMapper,
                                         WarehouseAreaMapper warehouseAreaMapper, PageAdapter pageAdapter) {
        this.plantMapper = plantMapper;
        this.warehouseMapper = warehouseMapper;
        this.warehouseAreaMapper = warehouseAreaMapper;
        this.pageAdapter = pageAdapter;
    }

    /** 在已启用 Plant 下创建 Warehouse，同一 Plant 内 Code 唯一。 */
    @Transactional
    public WarehouseView createWarehouse(String plantId, String code, String nameZh, String nameEn, String status) {
        PlantEntity plant = plantMapper.selectById(MdmMasterDataRules.id(plantId, "plantId"));
        if (plant == null) throw MdmException.notFound("Plant");
        MdmMasterDataRules.requireEnabled(plant.getStatus(), "Plant");
        WarehouseEntity entity = new WarehouseEntity();
        entity.setPlantId(plant.getId());
        entity.setCode(MdmMasterDataRules.code(code));
        setNames(entity, nameZh, nameEn);
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> warehouseMapper.insert(entity), "Warehouse");
        return toView(entity);
    }

    /** 更新 Warehouse 名称，不允许改变 Plant 或 Code。 */
    @Transactional
    public WarehouseView updateWarehouse(String id, String nameZh, String nameEn, Long version) {
        WarehouseEntity entity = requireWarehouse(id);
        requireVersion(entity.getVersion(), version);
        setNames(entity, nameZh, nameEn);
        requireUpdated(warehouseMapper.updateById(entity), () -> warehouseMapper.selectById(entity.getId()), "Warehouse");
        return toView(entity);
    }

    /** 按 ID 查询 Warehouse。 */
    @Transactional(readOnly = true)
    public WarehouseView getWarehouse(String id) { return toView(requireWarehouse(id)); }

    /**
     * 可按 Plant 过滤并稳定排序分页 Warehouse。
     *
     * @param pageQuery 包含可选 Plant ID 和分页信息的唯一业务入参
     * @return 统一分页结果；只读、幂等且无持久化副作用
     * @throws MdmException Plant ID 格式非法时抛出
     */
    @Transactional(readOnly = true)
    public PageResult<WarehouseView> pageWarehouses(PageQuery<WarehousePageParams> pageQuery) {
        WarehousePageParams params = pageQuery.params();
        Page<WarehouseEntity> page = pageAdapter.toPage(pageQuery);
        LambdaQueryWrapper<WarehouseEntity> query = new LambdaQueryWrapper<>();
        String plantId = params.plantId();
        if (plantId != null && !plantId.isBlank()) query.eq(WarehouseEntity::getPlantId, MdmMasterDataRules.id(plantId, "plantId"));
        query.orderByAsc(WarehouseEntity::getCode).orderByAsc(WarehouseEntity::getId);
        warehouseMapper.selectPage(page, query);
        return pageAdapter.toResult(page, WarehouseStructureApplication::toView);
    }

    /** 启用 Warehouse，不级联修改 WarehouseArea。 */
    @Transactional
    public WarehouseView enableWarehouse(String id, Long version) { return changeWarehouseStatus(id, MdmMasterDataRules.ENABLED, version); }

    /** 停用 Warehouse，不级联修改 WarehouseArea。 */
    @Transactional
    public WarehouseView disableWarehouse(String id, Long version) { return changeWarehouseStatus(id, MdmMasterDataRules.DISABLED, version); }

    /** 在已启用 Warehouse 下创建 WarehouseArea，同一 Warehouse 内 Code 唯一。 */
    @Transactional
    public WarehouseAreaView createWarehouseArea(String warehouseId, String code, String nameZh,
                                                 String nameEn, String status) {
        WarehouseEntity warehouse = requireWarehouse(warehouseId);
        MdmMasterDataRules.requireEnabled(warehouse.getStatus(), "Warehouse");
        WarehouseAreaEntity entity = new WarehouseAreaEntity();
        entity.setWarehouseId(warehouse.getId());
        entity.setCode(MdmMasterDataRules.code(code));
        setNames(entity, nameZh, nameEn);
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> warehouseAreaMapper.insert(entity), "WarehouseArea");
        return toView(entity);
    }

    /** 更新 WarehouseArea 名称，不允许改变 Warehouse 或 Code。 */
    @Transactional
    public WarehouseAreaView updateWarehouseArea(String id, String nameZh, String nameEn, Long version) {
        WarehouseAreaEntity entity = requireWarehouseArea(id);
        requireVersion(entity.getVersion(), version);
        setNames(entity, nameZh, nameEn);
        requireUpdated(warehouseAreaMapper.updateById(entity),
                () -> warehouseAreaMapper.selectById(entity.getId()), "WarehouseArea");
        return toView(entity);
    }

    /** 按 ID 查询 WarehouseArea。 */
    @Transactional(readOnly = true)
    public WarehouseAreaView getWarehouseArea(String id) { return toView(requireWarehouseArea(id)); }

    /**
     * 可按 Warehouse 过滤并稳定排序分页 WarehouseArea。
     *
     * @param pageQuery 包含可选 Warehouse ID 和分页信息的唯一业务入参
     * @return 统一分页结果；只读、幂等且无持久化副作用
     * @throws MdmException Warehouse ID 格式非法时抛出
     */
    @Transactional(readOnly = true)
    public PageResult<WarehouseAreaView> pageWarehouseAreas(PageQuery<WarehouseAreaPageParams> pageQuery) {
        WarehouseAreaPageParams params = pageQuery.params();
        Page<WarehouseAreaEntity> page = pageAdapter.toPage(pageQuery);
        LambdaQueryWrapper<WarehouseAreaEntity> query = new LambdaQueryWrapper<>();
        String warehouseId = params.warehouseId();
        if (warehouseId != null && !warehouseId.isBlank()) query.eq(WarehouseAreaEntity::getWarehouseId,
                MdmMasterDataRules.id(warehouseId, "warehouseId"));
        query.orderByAsc(WarehouseAreaEntity::getCode).orderByAsc(WarehouseAreaEntity::getId);
        warehouseAreaMapper.selectPage(page, query);
        return pageAdapter.toResult(page, WarehouseStructureApplication::toView);
    }

    /** 启用 WarehouseArea。 */
    @Transactional
    public WarehouseAreaView enableWarehouseArea(String id, Long version) { return changeAreaStatus(id, MdmMasterDataRules.ENABLED, version); }

    /** 停用 WarehouseArea。 */
    @Transactional
    public WarehouseAreaView disableWarehouseArea(String id, Long version) { return changeAreaStatus(id, MdmMasterDataRules.DISABLED, version); }

    private WarehouseView changeWarehouseStatus(String id, String status, Long version) {
        WarehouseEntity entity = requireWarehouse(id);
        requireVersion(entity.getVersion(), version);
        if (MdmMasterDataRules.ENABLED.equals(status)) {
            PlantEntity plant = plantMapper.selectById(entity.getPlantId());
            if (plant == null) throw MdmException.notFound("Plant");
            MdmMasterDataRules.requireEnabled(plant.getStatus(), "Plant");
        }
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(warehouseMapper.updateById(entity), () -> warehouseMapper.selectById(entity.getId()), "Warehouse");
        return toView(entity);
    }

    private WarehouseAreaView changeAreaStatus(String id, String status, Long version) {
        WarehouseAreaEntity entity = requireWarehouseArea(id);
        requireVersion(entity.getVersion(), version);
        if (MdmMasterDataRules.ENABLED.equals(status)) {
            MdmMasterDataRules.requireEnabled(requireWarehouse(entity.getWarehouseId()).getStatus(), "Warehouse");
        }
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(warehouseAreaMapper.updateById(entity),
                () -> warehouseAreaMapper.selectById(entity.getId()), "WarehouseArea");
        return toView(entity);
    }

    private WarehouseEntity requireWarehouse(String id) {
        WarehouseEntity entity = warehouseMapper.selectById(MdmMasterDataRules.id(id, "warehouseId"));
        if (entity == null) throw MdmException.notFound("Warehouse");
        return entity;
    }

    private WarehouseAreaEntity requireWarehouseArea(String id) {
        WarehouseAreaEntity entity = warehouseAreaMapper.selectById(MdmMasterDataRules.id(id, "warehouseAreaId"));
        if (entity == null) throw MdmException.notFound("WarehouseArea");
        return entity;
    }

    private static void insert(IntOperation operation, String resourceName) {
        try { operation.execute(); } catch (DuplicateKeyException exception) { throw MdmException.codeConflict(resourceName); }
    }

    private static void requireUpdated(int affected, EntityLookup lookup, String resourceName) {
        if (affected == 1) return;
        if (lookup.find() == null) throw MdmException.notFound(resourceName);
        throw MdmException.versionConflict();
    }

    private static void setNames(WarehouseEntity e, String zh, String en) { e.setNameZh(MdmMasterDataRules.nameZh(zh)); e.setNameEn(MdmMasterDataRules.nameEn(en)); }
    private static void setNames(WarehouseAreaEntity e, String zh, String en) { e.setNameZh(MdmMasterDataRules.nameZh(zh)); e.setNameEn(MdmMasterDataRules.nameEn(en)); }
    private static WarehouseView toView(WarehouseEntity e) { return new WarehouseView(e.getId(), e.getCode(), e.getNameZh(), e.getNameEn(), e.getPlantId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    private static WarehouseAreaView toView(WarehouseAreaEntity e) { return new WarehouseAreaView(e.getId(), e.getCode(), e.getNameZh(), e.getNameEn(), e.getWarehouseId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }

    @FunctionalInterface private interface IntOperation { int execute(); }
    @FunctionalInterface private interface EntityLookup { Object find(); }
}
