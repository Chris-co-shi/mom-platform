package io.github.chrisshi.mom.wms.application;

import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.wms.infrastructure.query.InventoryHistoryQueryMapper;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 库存不可变交易与分录的只读查询用例；不以当前余额补写历史空值。 */
@Service
@RequiredArgsConstructor
public class InventoryHistoryApplication {
    private final InventoryHistoryQueryMapper mapper;
    private final PageAdapter pages;

    

    /** 交易时间采用左闭右开区间，最长 366 天；可按分录中的历史名称和编码筛选。 */
    public record Filter(String materialId, String batchCode, String snCode, String warehouseId,
                         String locationId, String transactionType, String sourceType, String sourceId,
                         String actor, String materialKeyword, String warehouseCode, String locationCode,
                         Instant startTime, Instant endTime) { }

    /** 单条分录及其所属交易事实；数量只代表本分录对应的完整库存维度。 */
    @Getter @Setter
    public static class EntryView {
        private String id;
        private String transactionId;
        private Integer lineNo;
        private String transactionType;
        private String sourceType;
        private String sourceId;
        private String operatorId;
        private Instant occurredAt;
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
        @JsonFormat(shape = JsonFormat.Shape.STRING) private BigDecimal deltaQuantity;
        @JsonFormat(shape = JsonFormat.Shape.STRING) private BigDecimal beforeQuantity;
        @JsonFormat(shape = JsonFormat.Shape.STRING) private BigDecimal afterQuantity;
    }

    /** 交易基本事实和全部分录；原因只取同次业务已保存的历史说明。 */
    @Getter @Setter
    public static class TransactionView {
        private String id;
        private String transactionType;
        private String sourceType;
        private String sourceId;
        private String operatorId;
        private Instant occurredAt;
        private String reasonCode;
        private String note;
        private List<EntryView> entries;
    }

    /**
     * 按分录分页查询真实历史，时间区间为左闭右开且不得超过 366 天。
     * @param query 分页及组合筛选条件
     * @return 不合并正负分录的历史页
     * @throws IllegalArgumentException 时间边界非法时抛出
     */
    @Transactional(readOnly = true)
    public PageResult<EntryView> search(PageQuery<Filter> query) {
        Filter f = query.params();
        if (f.startTime() == null || f.endTime() == null || !f.startTime().isBefore(f.endTime())
                || Duration.between(f.startTime(), f.endTime()).compareTo(Duration.ofDays(366)) > 0) {
            throw new IllegalArgumentException("库存历史查询须指定最长 366 天的有效时间区间");
        }
        Filter filter = new Filter(clean(f.materialId()), clean(f.batchCode()), clean(f.snCode()),
                clean(f.warehouseId()), clean(f.locationId()), clean(f.transactionType()),
                clean(f.sourceType()), clean(f.sourceId()), clean(f.actor()), clean(f.materialKeyword()),
                clean(f.warehouseCode()), clean(f.locationCode()), f.startTime(), f.endTime());
        Page<EntryView> page = pages.toPage(query);
        mapper.search(page, filter);
        return pages.toResult(page);
    }

    /**
     * 查询一笔交易的所有分录，详情不继承列表筛选条件。
     * @param transactionId 交易标识
     * @return 交易事实与按行号排序的全部分录，不存在时为空
     */
    @Transactional(readOnly = true)
    public TransactionView transaction(String transactionId) {
        if (transactionId == null || !transactionId.matches("[0-9]{1,19}")) {
            throw new IllegalArgumentException("交易 ID 不合法");
        }
        TransactionView view = mapper.transaction(transactionId);
        if (view == null) return null;
        view.setEntries(mapper.entries(transactionId));
        return view;
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
