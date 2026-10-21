package io.github.chrisshi.mom.wms.application;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryBalanceEntity;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryCountLineEntity;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryCountTaskEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 盘点用例的稳定输出模型及 Entity 映射。仅负责业务数据表达，不调用 SQL、Mapper 或远端系统。
 * 与 SQL 操作不同，本类属于 Application 而不是 Infrastructure。
 */
public final class InventoryCountModels {
    private InventoryCountModels() { }

    public record Task(String id, String taskNo, String requestKey, String warehouseId, String warehouseCode,
                       List<String> locationIds, List<String> materialIds, String status, String transactionId,
                       String createdBy, Instant createdAt, String updatedBy, Instant updatedAt, String startedBy,
                       Instant startedAt, String confirmedBy, Instant confirmedAt, String completedBy,
                       Instant completedAt, String cancelledBy, Instant cancelledAt, long version) { }

    public record Line(String id, String taskId, Dimension dimension, BigDecimal bookQuantity,
                       BigDecimal countedQuantity, String snapshotBy, Instant snapshotAt,
                       String countedBy, Instant countedAt) { }

    private static List<String> list(String[] v) {
        return v == null ? List.of() : List.copyOf(Arrays.asList(v));
    }

    public static Task task(InventoryCountTaskEntity row) {
        if (row == null) return null;
        return new Task(row.getId(), row.getTaskNo(), row.getRequestKey(), row.getWarehouseId(),
                row.getWarehouseCode(), list(row.getLocationIds()), list(row.getMaterialIds()), row.getStatus(),
                row.getTransactionId(), row.getCreatedBy(), row.getCreatedAt(), row.getUpdatedBy(),
                row.getUpdatedAt(), row.getStartedBy(), row.getStartedAt(), row.getConfirmedBy(),
                row.getConfirmedAt(), row.getCompletedBy(), row.getCompletedAt(), row.getCancelledBy(),
                row.getCancelledAt(), row.getVersion());
    }

    public static Dimension dimension(InventoryCountLineEntity row) {
        return new Dimension(row.getMaterialId(), row.getMaterialCode(), row.getMaterialName(),
                row.getUomCode(), row.getBatchCode(), row.getSnCode(), row.getWarehouseId(),
                row.getWarehouseCode(), row.getLocationId(), row.getLocationCode(),
                row.getQualityStatus(), row.getWarehouseStatus());
    }

    public static Dimension dimension(InventoryBalanceEntity row) {
        return new Dimension(row.getMaterialId(), row.getMaterialCode(), row.getMaterialName(),
                row.getUomCode(), row.getBatchCode(), row.getSnCode(), row.getWarehouseId(),
                row.getWarehouseCode(), row.getLocationId(), row.getLocationCode(),
                row.getQualityStatus(), row.getWarehouseStatus());
    }

    public static Line line(InventoryCountLineEntity row) {
        return new Line(row.getId(), row.getTaskId(), dimension(row), row.getBookQuantity(),
                row.getCountedQuantity(), row.getSnapshotBy(), row.getSnapshotAt(),
                row.getCountedBy(), row.getCountedAt());
    }

    public static InventoryCountLineEntity lineRow(String id, String taskId, Dimension dimension, String actor) {
        InventoryCountLineEntity row = new InventoryCountLineEntity();
        row.setId(id);
        row.setTaskId(taskId);
        row.setMaterialId(dimension.materialId());
        row.setMaterialCode(dimension.materialCode());
        row.setMaterialName(dimension.materialName());
        row.setUomCode(dimension.uomCode());
        row.setBatchCode(dimension.batchCode());
        row.setSnCode(dimension.snCode());
        row.setWarehouseId(dimension.warehouseId());
        row.setWarehouseCode(dimension.warehouseCode());
        row.setLocationId(dimension.locationId());
        row.setLocationCode(dimension.locationCode());
        row.setQualityStatus(dimension.qualityStatus());
        row.setWarehouseStatus(dimension.warehouseStatus());
        row.setSnapshotBy(actor);
        return row;
    }
}
