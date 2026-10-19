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

    /** @param mapper WMS 余额只读 Mapper @param pages 平台分页适配器 */
    public InventoryQueryService(InventoryQueryMapper mapper, PageAdapter pages) {
        this.mapper = mapper;
        this.pages = pages;
    }

    /** 总览组合条件，空串由 Application 转为无过滤条件。 */
    public record OverviewFilter(String materialId, String keyword, String warehouseId) {}
    /** 明细组合条件，batch/sn 为精确筛选。 */
    public record DetailFilter(String materialId, String warehouseId, String locationId,
                               String batchCode, String snCode, String qualityStatus, String warehouseStatus) {}

    /** Material 汇总视图；冻结量包含在不可用量中。 */
    @Getter @Setter
    public static class OverviewRow {
        private String materialId;
        private String materialCode;
        private String materialName;
        private String uomCode;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal totalQuantity;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal availableQuantity;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal unavailableQuantity;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal frozenQuantity;
    }

    /** 非零余额明细视图。 */
    @Getter @Setter
    public static class DetailRow {
        private String id;
        private String materialId;
        private String materialCode;
        private String materialName;
        private String uomCode;
        private String batchCode;
        private String snCode;
        private String warehouseId;
        private String warehouseCode;
        private String locationId;
        private String locationCode;
        private String qualityStatus;
        private String warehouseStatus;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal quantity;
    }

    /** 按 Material 聚合非零余额并执行数据库分页。 */
    @Transactional(readOnly = true)
    public PageResult<OverviewRow> overview(PageQuery<OverviewFilter> query) {
        OverviewFilter f = query.params();
        var filter = new OverviewFilter(clean(f.materialId()), clean(f.keyword()), clean(f.warehouseId()));
        Page<OverviewRow> page = pages.toPage(query);
        mapper.overview(page, filter);
        return pages.toResult(page);
    }

    /** 返回非零余额的组合筛选明细，保留 SN 多状态余额。 */
    @Transactional(readOnly = true)
    public PageResult<DetailRow> details(PageQuery<DetailFilter> query) {
        DetailFilter f = query.params();
        if (f.qualityStatus() != null && !f.qualityStatus().isBlank()
                && !java.util.List.of("PENDING", "QUALIFIED", "RESTRICTED", "REJECTED").contains(f.qualityStatus()))
            throw new IllegalArgumentException("质量状态非法");
        if (f.warehouseStatus() != null && !f.warehouseStatus().isBlank()
                && !java.util.List.of("NORMAL", "FROZEN").contains(f.warehouseStatus()))
            throw new IllegalArgumentException("仓储状态非法");
        var filter = new DetailFilter(clean(f.materialId()), clean(f.warehouseId()), clean(f.locationId()),
                clean(f.batchCode()), clean(f.snCode()), clean(f.qualityStatus()), clean(f.warehouseStatus()));
        Page<DetailRow> page = pages.toPage(query);
        mapper.details(page, filter);
        return pages.toResult(page);
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
