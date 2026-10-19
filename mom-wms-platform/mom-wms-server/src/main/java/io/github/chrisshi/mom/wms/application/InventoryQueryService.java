package io.github.chrisshi.mom.wms.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.wms.infrastructure.query.InventoryQueryMapper;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 从唯一余额表现算实时库存，不维护第二份汇总表。 */
@Service
public class InventoryQueryService {
    private final InventoryQueryMapper mapper;
    private final PageAdapter pages;

    /**
     * @param mapper WMS 余额只读 Mapper
     * @param pages 平台分页适配器
     */
    public InventoryQueryService(InventoryQueryMapper mapper, PageAdapter pages) {
        this.mapper = mapper;
        this.pages = pages;
    }

    /**
     * 总览组合条件，空串由 Application 转为无过滤条件。
     * @param materialId 物料稳定标识
     * @param keyword 物料编码或名称模糊查询词
     * @param warehouseId 仓库稳定标识
     * @param warehouseCode 仓库编码模糊查询词
     */
    public record OverviewFilter(String materialId, String keyword, String warehouseId, String warehouseCode) {}
    /**
     * 明细组合条件，批次和 SN 为精确筛选，状态以业务枚举承载。
     * @param materialId 物料稳定标识
     * @param warehouseId 仓库稳定标识
     * @param locationId 库位稳定标识
     * @param batchCode 批次编码
     * @param snCode 序列号
     * @param qualityStatus 质量状态
     * @param warehouseStatus 仓储状态
     */
    public record DetailFilter(String materialId, String warehouseId, String locationId,
                               String batchCode, String snCode, QualityStatus qualityStatus,
                               WarehouseStatus warehouseStatus) {}

    /** Material 汇总视图；冻结量包含在不可用量中。 */
    @Getter @Setter
    public static class OverviewRow {
        /** 物料稳定标识。 */
        private String materialId;
        /** 物料编码展示快照。 */
        private String materialCode;
        /** 物料名称展示快照。 */
        private String materialName;
        /** 汇总数量使用的计量单位。 */
        private String uomCode;
        /** 当前物料在筛选范围内的总库存数量。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal totalQuantity;
        /** QUALIFIED 且 NORMAL 状态的可用数量。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal availableQuantity;
        /** 除可用量外的其他状态数量，冻结量包含在其中。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal unavailableQuantity;
        /** FROZEN 状态数量，是不可用数量的子集。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal frozenQuantity;
    }

    /** 非零余额明细视图。 */
    @Getter @Setter
    public static class DetailRow {
        /** 余额技术标识。 */
        private String id;
        /** 物料稳定标识。 */
        private String materialId;
        /** 物料编码展示快照。 */
        private String materialCode;
        /** 物料名称展示快照。 */
        private String materialName;
        /** 库存数量计量单位。 */
        private String uomCode;
        /** 批次维度值；非批次物料为空。 */
        private String batchCode;
        /** SN 维度值；非 SN 管理物料为空。 */
        private String snCode;
        /** 仓库稳定标识。 */
        private String warehouseId;
        /** 仓库编码快照。 */
        private String warehouseCode;
        /** 库位稳定标识。 */
        private String locationId;
        /** 库位编码快照。 */
        private String locationCode;
        /** 当前余额维度质量状态。 */
        private QualityStatus qualityStatus;
        /** 当前余额维度仓储状态。 */
        private WarehouseStatus warehouseStatus;
        /** 当前唯一余额维度上的非零数量。 */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal quantity;
    }

    /**
     * 按 Material 聚合非零余额并执行数据库分页。
     * @param query 分页与过滤条件
     * @return 实时汇总页
     */
    @Transactional(readOnly = true)
    public PageResult<OverviewRow> overview(PageQuery<OverviewFilter> query) {
        OverviewFilter f = query.params();
        var filter = new OverviewFilter(clean(f.materialId()), clean(f.keyword()), clean(f.warehouseId()), clean(f.warehouseCode()));
        Page<OverviewRow> page = pages.toPage(query);
        mapper.overview(page, filter, QualityStatus.QUALIFIED, WarehouseStatus.NORMAL);
        return pages.toResult(page);
    }

    /**
     * 返回非零余额的组合筛选明细，保留 SN 多状态余额。
     * @param query 分页与过滤条件
     * @return 实时库存明细页
     */
    @Transactional(readOnly = true)
    public PageResult<DetailRow> details(PageQuery<DetailFilter> query) {
        DetailFilter f = query.params();
        var filter = new DetailFilter(clean(f.materialId()), clean(f.warehouseId()), clean(f.locationId()),
                clean(f.batchCode()), clean(f.snCode()), f.qualityStatus(), f.warehouseStatus());
        Page<DetailRow> page = pages.toPage(query);
        mapper.details(page, filter);
        return pages.toResult(page);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
