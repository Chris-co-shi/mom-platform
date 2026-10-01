package io.github.chrisshi.mom.mdm.application;

/**
 * MDM 分页用例的强类型过滤参数集合。
 *
 * <p>这些不可变记录属于 Application 入站模型，由 Controller 通过 {@code PageQuery<XxxPageParams>}
 * 反序列化并原样交给对应 Application。它们不依赖 HTTP、MyBatis Entity 或 Mapper；Application 负责
 * 将非空字段转换为受控查询条件。记录无共享可变状态且线程安全，空对象表示不附加可选过滤条件。</p>
 */
public final class MdmPageParams {
    private MdmPageParams() {
    }

    /** Plant 当前没有可选过滤条件。 */
    public record PlantPageParams() { }

    /** @param plantId 可选 Plant ID */
    public record WorkshopPageParams(String plantId) { }

    /** @param workshopId 可选 Workshop ID */
    public record ProductionLinePageParams(String workshopId) { }

    /** @param productionLineId 可选 ProductionLine ID */
    public record WorkstationPageParams(String productionLineId) { }

    /** @param plantId 可选 Plant ID */
    public record WarehousePageParams(String plantId) { }

    /** @param warehouseId 可选 Warehouse ID */
    public record WarehouseAreaPageParams(String warehouseId) { }

    /** LocationType 当前没有可选过滤条件。 */
    public record LocationTypePageParams() { }

    /**
     * @param plantId 可选 Plant ID
     * @param warehouseAreaId 可选 WarehouseArea ID
     */
    public record LocationPageParams(String plantId, String warehouseAreaId) { }

    /**
     * @param parentId 可选父分类 ID，仅查询直接子级
     * @param status 可选 ENABLED/DISABLED 状态
     */
    public record MaterialCategoryPageParams(String parentId, String status) { }

    /**
     * @param code 可选平台唯一物料编码精确匹配
     * @param keyword 可选编码或名称包含匹配
     * @param categoryId 可选直接分类 ID，不递归包含后代
     * @param baseUomId 可选基础计量单位 ID
     * @param status 可选 ENABLED/DISABLED 状态
     */
    public record MaterialPageParams(
            String code, String keyword, String categoryId, String baseUomId, String status) { }

    /** @param status 可选 ENABLED/DISABLED 状态 */
    public record DimensionPageParams(String status) { }

    /**
     * @param dimensionId 可选 Dimension ID
     * @param status 可选 ENABLED/DISABLED 状态
     */
    public record UomCategoryPageParams(String dimensionId, String status) { }

    /**
     * @param categoryId 可选 UOM Category ID
     * @param status 可选 ENABLED/DISABLED 状态
     * @param referenceUnit 可选基准单位标记
     */
    public record UomPageParams(String categoryId, String status, Boolean referenceUnit) { }

    /** @param uomId 必填 UOM ID */
    public record UomConversionRulePageParams(String uomId) { }
}
