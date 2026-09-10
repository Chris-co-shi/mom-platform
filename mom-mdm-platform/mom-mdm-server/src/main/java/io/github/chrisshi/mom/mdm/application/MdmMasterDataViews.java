package io.github.chrisshi.mom.mdm.application;

import java.time.Instant;

/**
 * 第一组 MDM 主数据的 Application 查询视图集合。
 *
 * <p>这些不可变记录隔离数据库 Entity 与 HTTP 响应，不包含 Result 或 MyBatis 类型。记录只表达已提交的
 * 本地 PostgreSQL 状态，不承诺跨服务缓存、事件发布或运行时占用事实。</p>
 */
public final class MdmMasterDataViews {
    private MdmMasterDataViews() {
    }

    /** Plant 详情与分页视图。 */
    public record PlantView(String id, String code, String nameZh, String nameEn, String status,
                            Instant createdAt, String createdBy, Instant updatedAt, String updatedBy, Long version) { }

    /** Workshop 详情与分页视图。 */
    public record WorkshopView(String id, String code, String nameZh, String nameEn, String plantId, String status,
                               Instant createdAt, String createdBy, Instant updatedAt, String updatedBy, Long version) { }

    /** ProductionLine 详情与分页视图。 */
    public record ProductionLineView(String id, String code, String nameZh, String nameEn, String workshopId,
                                     String status, Instant createdAt, String createdBy, Instant updatedAt,
                                     String updatedBy, Long version) { }

    /** Workstation 详情与分页视图。 */
    public record WorkstationView(String id, String code, String nameZh, String nameEn, String productionLineId,
                                  String status, Instant createdAt, String createdBy, Instant updatedAt,
                                  String updatedBy, Long version) { }

    /** Warehouse 详情与分页视图。 */
    public record WarehouseView(String id, String code, String nameZh, String nameEn, String plantId, String status,
                                Instant createdAt, String createdBy, Instant updatedAt, String updatedBy, Long version) { }

    /** WarehouseArea 详情与分页视图。 */
    public record WarehouseAreaView(String id, String code, String nameZh, String nameEn, String warehouseId,
                                    String status, Instant createdAt, String createdBy, Instant updatedAt,
                                    String updatedBy, Long version) { }

    /** 动态 LocationType 详情与分页视图。 */
    public record LocationTypeView(String id, String code, String nameZh, String nameEn, String status,
                                   Instant createdAt, String createdBy, Instant updatedAt, String updatedBy,
                                   Long version) { }

    /** 统一可寻址 Location 详情与分页视图。 */
    public record LocationView(String id, String code, String nameZh, String nameEn, String plantId,
                               String warehouseAreaId, String locationTypeId, String status, Instant createdAt,
                               String createdBy, Instant updatedAt, String updatedBy, Long version) { }
}
