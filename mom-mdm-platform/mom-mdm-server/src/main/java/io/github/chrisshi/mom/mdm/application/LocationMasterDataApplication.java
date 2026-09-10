package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.LocationTypeView;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.LocationView;
import io.github.chrisshi.mom.mdm.infrastructure.entity.LocationEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.LocationTypeEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.PlantEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WarehouseAreaEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WarehouseEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.LocationMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.LocationTypeMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.PlantMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.WarehouseAreaMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.WarehouseMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * LocationType 与统一可寻址 Location 的 Level 1 用例及本地事务边界。
 *
 * <p>LocationType 是动态分类，代码中不按 Type Code 分支。Location 创建时校验 Plant、LocationType 以及
 * 可选 WarehouseArea 的完整关系；不保存占用、预留、库存、容器或 AGV 运行时事实。</p>
 */
@Component
public class LocationMasterDataApplication {
    private final PlantMapper plantMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseAreaMapper warehouseAreaMapper;
    private final LocationTypeMapper locationTypeMapper;
    private final LocationMapper locationMapper;

    /** 注入 Location 本地引用校验及单表持久化 Mapper。 */
    public LocationMasterDataApplication(PlantMapper plantMapper, WarehouseMapper warehouseMapper,
                                         WarehouseAreaMapper warehouseAreaMapper, LocationTypeMapper locationTypeMapper,
                                         LocationMapper locationMapper) {
        this.plantMapper = plantMapper;
        this.warehouseMapper = warehouseMapper;
        this.warehouseAreaMapper = warehouseAreaMapper;
        this.locationTypeMapper = locationTypeMapper;
        this.locationMapper = locationMapper;
    }

    /** 创建平台唯一 Code 的动态 LocationType。 */
    @Transactional
    public LocationTypeView createLocationType(String code, String nameZh, String nameEn, String status) {
        LocationTypeEntity entity = new LocationTypeEntity();
        entity.setCode(MdmMasterDataRules.code(code));
        setNames(entity, nameZh, nameEn);
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> locationTypeMapper.insert(entity), "LocationType");
        return toView(entity);
    }

    /** 更新 LocationType 名称，不允许修改 Code。 */
    @Transactional
    public LocationTypeView updateLocationType(String id, String nameZh, String nameEn, Long version) {
        LocationTypeEntity entity = requireLocationType(id);
        requireVersion(entity.getVersion(), version);
        setNames(entity, nameZh, nameEn);
        requireUpdated(locationTypeMapper.updateById(entity), () -> locationTypeMapper.selectById(entity.getId()),
                "LocationType");
        return toView(entity);
    }

    /** 按 ID 查询 LocationType。 */
    @Transactional(readOnly = true)
    public LocationTypeView getLocationType(String id) { return toView(requireLocationType(id)); }

    /** 稳定排序分页查询 LocationType，分页转换复用 PageAdapter。 */
    @Transactional(readOnly = true)
    public PageResult<LocationTypeView> pageLocationTypes(long pageNo, long pageSize) {
        Page<LocationTypeEntity> page = PageAdapter.toPage(new PageQuery<>(null, pageNo, pageSize));
        locationTypeMapper.selectPage(page, new LambdaQueryWrapper<LocationTypeEntity>()
                .orderByAsc(LocationTypeEntity::getCode).orderByAsc(LocationTypeEntity::getId));
        return PageAdapter.toResult(page, LocationMasterDataApplication::toView);
    }

    /** 启用 LocationType，不按类型 Code 执行任何业务分支。 */
    @Transactional
    public LocationTypeView enableLocationType(String id, Long version) { return changeTypeStatus(id, MdmMasterDataRules.ENABLED, version); }

    /** 停用 LocationType，不级联停用已有 Location。 */
    @Transactional
    public LocationTypeView disableLocationType(String id, Long version) { return changeTypeStatus(id, MdmMasterDataRules.DISABLED, version); }

    /**
     * 创建 Location；WarehouseArea 可空，非空时必须通过 Warehouse 归属于同一 Plant。
     *
     * @throws MdmException Plant、LocationType、WarehouseArea 不存在或区域与 Plant 不一致时抛出
     */
    @Transactional
    public LocationView createLocation(String plantId, String warehouseAreaId, String locationTypeId, String code,
                                       String nameZh, String nameEn, String status) {
        PlantEntity plant = requirePlant(plantId);
        LocationTypeEntity locationType = requireLocationType(locationTypeId);
        String validatedAreaId = validateAreaBelongsToPlant(warehouseAreaId, plant.getId());
        LocationEntity entity = new LocationEntity();
        entity.setPlantId(plant.getId());
        entity.setWarehouseAreaId(validatedAreaId);
        entity.setLocationTypeId(locationType.getId());
        entity.setCode(MdmMasterDataRules.code(code));
        setNames(entity, nameZh, nameEn);
        entity.setStatus(MdmMasterDataRules.status(status));
        insert(() -> locationMapper.insert(entity), "Location");
        return toView(entity);
    }

    /** 更新 Location 名称，不允许改变 Code、Plant、WarehouseArea 或 LocationType。 */
    @Transactional
    public LocationView updateLocation(String id, String nameZh, String nameEn, Long version) {
        LocationEntity entity = requireLocation(id);
        requireVersion(entity.getVersion(), version);
        setNames(entity, nameZh, nameEn);
        requireUpdated(locationMapper.updateById(entity), () -> locationMapper.selectById(entity.getId()), "Location");
        return toView(entity);
    }

    /** 按 ID 查询 Location。 */
    @Transactional(readOnly = true)
    public LocationView getLocation(String id) { return toView(requireLocation(id)); }

    /** 可按 Plant 过滤并稳定排序分页查询 Location。 */
    @Transactional(readOnly = true)
    public PageResult<LocationView> pageLocations(String plantId, long pageNo, long pageSize) {
        Page<LocationEntity> page = PageAdapter.toPage(new PageQuery<>(plantId, pageNo, pageSize));
        LambdaQueryWrapper<LocationEntity> query = new LambdaQueryWrapper<>();
        if (plantId != null && !plantId.isBlank()) query.eq(LocationEntity::getPlantId,
                MdmMasterDataRules.id(plantId, "plantId"));
        query.orderByAsc(LocationEntity::getCode).orderByAsc(LocationEntity::getId);
        locationMapper.selectPage(page, query);
        return PageAdapter.toResult(page, LocationMasterDataApplication::toView);
    }

    /** 启用 Location。 */
    @Transactional
    public LocationView enableLocation(String id, Long version) { return changeLocationStatus(id, MdmMasterDataRules.ENABLED, version); }

    /** 停用 Location，不产生库存、容器或预留副作用。 */
    @Transactional
    public LocationView disableLocation(String id, Long version) { return changeLocationStatus(id, MdmMasterDataRules.DISABLED, version); }

    private LocationTypeView changeTypeStatus(String id, String status, Long version) {
        LocationTypeEntity entity = requireLocationType(id);
        requireVersion(entity.getVersion(), version);
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(locationTypeMapper.updateById(entity), () -> locationTypeMapper.selectById(entity.getId()),
                "LocationType");
        return toView(entity);
    }

    private LocationView changeLocationStatus(String id, String status, Long version) {
        LocationEntity entity = requireLocation(id);
        requireVersion(entity.getVersion(), version);
        if (status.equals(entity.getStatus())) return toView(entity);
        entity.setStatus(status);
        requireUpdated(locationMapper.updateById(entity), () -> locationMapper.selectById(entity.getId()), "Location");
        return toView(entity);
    }

    private String validateAreaBelongsToPlant(String areaId, String plantId) {
        if (areaId == null || areaId.isBlank()) return null;
        WarehouseAreaEntity area = warehouseAreaMapper.selectById(MdmMasterDataRules.id(areaId, "warehouseAreaId"));
        if (area == null) throw MdmException.notFound("WarehouseArea");
        WarehouseEntity warehouse = warehouseMapper.selectById(area.getWarehouseId());
        if (warehouse == null) throw MdmException.notFound("Warehouse");
        if (!plantId.equals(warehouse.getPlantId())) {
            throw MdmException.invalidReference("WarehouseArea 不属于 Location 指定的 Plant");
        }
        return area.getId();
    }

    private PlantEntity requirePlant(String id) {
        PlantEntity entity = plantMapper.selectById(MdmMasterDataRules.id(id, "plantId"));
        if (entity == null) throw MdmException.notFound("Plant");
        return entity;
    }

    private LocationTypeEntity requireLocationType(String id) {
        LocationTypeEntity entity = locationTypeMapper.selectById(MdmMasterDataRules.id(id, "locationTypeId"));
        if (entity == null) throw MdmException.notFound("LocationType");
        return entity;
    }

    private LocationEntity requireLocation(String id) {
        LocationEntity entity = locationMapper.selectById(MdmMasterDataRules.id(id, "locationId"));
        if (entity == null) throw MdmException.notFound("Location");
        return entity;
    }

    private static void requireVersion(Long actual, Long expected) {
        long value = MdmMasterDataRules.version(expected);
        if (actual == null || actual != value) throw MdmException.versionConflict();
    }

    private static void insert(IntOperation operation, String resourceName) {
        try { operation.execute(); } catch (DuplicateKeyException exception) { throw MdmException.codeConflict(resourceName); }
    }

    private static void requireUpdated(int affected, EntityLookup lookup, String resourceName) {
        if (affected == 1) return;
        if (lookup.find() == null) throw MdmException.notFound(resourceName);
        throw MdmException.versionConflict();
    }

    private static void setNames(LocationTypeEntity e, String zh, String en) { e.setNameZh(MdmMasterDataRules.nameZh(zh)); e.setNameEn(MdmMasterDataRules.nameEn(en)); }
    private static void setNames(LocationEntity e, String zh, String en) { e.setNameZh(MdmMasterDataRules.nameZh(zh)); e.setNameEn(MdmMasterDataRules.nameEn(en)); }
    private static LocationTypeView toView(LocationTypeEntity e) { return new LocationTypeView(e.getId(), e.getCode(), e.getNameZh(), e.getNameEn(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }
    private static LocationView toView(LocationEntity e) { return new LocationView(e.getId(), e.getCode(), e.getNameZh(), e.getNameEn(), e.getPlantId(), e.getWarehouseAreaId(), e.getLocationTypeId(), e.getStatus(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), e.getVersion()); }

    @FunctionalInterface private interface IntOperation { int execute(); }
    @FunctionalInterface private interface EntityLookup { Object find(); }
}
