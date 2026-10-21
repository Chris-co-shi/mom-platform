package io.github.chrisshi.mom.wms.infrastructure.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryBalanceEntity;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryCountLineEntity;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryCountTaskEntity;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 盘点持久化兼容入口：保留既有 Application 读取契约与锁协议，类型化 CRUD 委托 mom-data Mapper。
 * 后续仅在端到端测试证实 API/事务行为不变后，评估消除本层剩余的适配方法。
 */
@Repository
public class InventoryCountPersistence {
    private final InventoryCountTaskMapper tasks;
    private final InventoryCountLineMapper lines;

    public InventoryCountPersistence(InventoryCountTaskMapper tasks, InventoryCountLineMapper lines) {
        this.tasks = tasks;
        this.lines = lines;
    }

    /** 保持已有 API JSON 字段形状的盘点任务只读视图；不得直接暴露可写 Entity。 */
    public record Task(String id, String taskNo, String requestKey, String warehouseId, String warehouseCode,
                       List<String> locationIds, List<String> materialIds, String status, String transactionId,
                       String createdBy, Instant createdAt, String updatedBy, Instant updatedAt, String startedBy,
                       Instant startedAt, String confirmedBy, Instant confirmedAt, String completedBy,
                       Instant completedAt, String cancelledBy, Instant cancelledAt, long version) { }

    /** 盘点明细视图：业务差异只依赖账面与实盘事实。 */
    public record Line(String id, String taskId, Dimension dimension, BigDecimal bookQuantity,
                       BigDecimal countedQuantity, String snapshotBy, Instant snapshotAt,
                       String countedBy, Instant countedAt) {
        public BigDecimal difference() { return countedQuantity.subtract(bookQuantity); }
    }

    /** 账面余额维度快照，包含已有零余额。 */
    public record Book(Dimension dimension, BigDecimal quantity) { }

    private static List<String> list(String[] values) {
        return values == null ? List.of() : List.copyOf(Arrays.asList(values));
    }

    private static Task task(InventoryCountTaskEntity row) {
        if (row == null) return null;
        return new Task(row.getId(), row.getTaskNo(), row.getRequestKey(), row.getWarehouseId(),
                row.getWarehouseCode(), list(row.getLocationIds()), list(row.getMaterialIds()), row.getStatus(),
                row.getTransactionId(), row.getCreatedBy(), row.getCreatedAt(), row.getUpdatedBy(),
                row.getUpdatedAt(), row.getStartedBy(), row.getStartedAt(), row.getConfirmedBy(),
                row.getConfirmedAt(), row.getCompletedBy(), row.getCompletedAt(), row.getCancelledBy(),
                row.getCancelledAt(), row.getVersion());
    }

    private static Dimension dimension(InventoryCountLineEntity row) {
        return new Dimension(row.getMaterialId(), row.getMaterialCode(), row.getMaterialName(),
                row.getUomCode(), row.getBatchCode(), row.getSnCode(), row.getWarehouseId(),
                row.getWarehouseCode(), row.getLocationId(), row.getLocationCode(),
                row.getQualityStatus(), row.getWarehouseStatus());
    }

    private static Dimension dimension(InventoryBalanceEntity row) {
        return new Dimension(row.getMaterialId(), row.getMaterialCode(), row.getMaterialName(),
                row.getUomCode(), row.getBatchCode(), row.getSnCode(), row.getWarehouseId(),
                row.getWarehouseCode(), row.getLocationId(), row.getLocationCode(),
                row.getQualityStatus(), row.getWarehouseStatus());
    }

    private static Line line(InventoryCountLineEntity row) {
        return new Line(row.getId(), row.getTaskId(), dimension(row), row.getBookQuantity(),
                row.getCountedQuantity(), row.getSnapshotBy(), row.getSnapshotAt(),
                row.getCountedBy(), row.getCountedAt());
    }

    private static InventoryCountLineEntity lineRow(String id,String taskId,Dimension d,String actor) {
        InventoryCountLineEntity row = new InventoryCountLineEntity();
        row.setId(id);
        row.setTaskId(taskId);
        row.setMaterialId(d.materialId());
        row.setMaterialCode(d.materialCode());
        row.setMaterialName(d.materialName());
        row.setUomCode(d.uomCode());
        row.setBatchCode(d.batchCode());
        row.setSnCode(d.snCode());
        row.setWarehouseId(d.warehouseId());
        row.setWarehouseCode(d.warehouseCode());
        row.setLocationId(d.locationId());
        row.setLocationCode(d.locationCode());
        row.setQualityStatus(d.qualityStatus());
        row.setWarehouseStatus(d.warehouseStatus());
        row.setSnapshotBy(actor);
        return row;
    }

    /** 创建草稿；普通插入及创建审计由 mom-data 处理。 */
    public void create(String id,String number,String requestKey,String warehouseId,String warehouseCode,
                       List<String> locations,List<String> materials,String actor) {
        InventoryCountTaskEntity row = new InventoryCountTaskEntity();
        row.setId(id);
        row.setTaskNo(number);
        row.setRequestKey(requestKey);
        row.setWarehouseId(warehouseId);
        row.setWarehouseCode(warehouseCode);
        row.setLocationIds(locations.toArray(String[]::new));
        row.setMaterialIds(materials.toArray(String[]::new));
        row.setCreatedBy(actor);
        row.setUpdatedBy(actor);
        if (tasks.insert(row) != 1) throw new IllegalStateException("盘点任务写入失败");
    }

    public Task find(String id) { return task(tasks.selectById(id)); }
    public void lockCreateRequest(String requestKey) { tasks.lockCreateRequest("WMS:COUNT:CREATE:" + requestKey); }
    public Task findByRequestKey(String requestKey) { return task(tasks.findByRequestKey(requestKey)); }
    public Task lockTask(String id) { return task(tasks.lockTask(id)); }

    public List<Line> lines(String taskId) {
        return lines.selectList(Wrappers.<InventoryCountLineEntity>lambdaQuery()
                        .eq(InventoryCountLineEntity::getTaskId, taskId)
                        .orderByAsc(InventoryCountLineEntity::getId))
                .stream().map(InventoryCountPersistence::line).toList();
    }

    public int updateScope(String id,List<String> locations,List<String> materials,String actor) {
        return tasks.updateScope(id,locations.toArray(String[]::new),materials.toArray(String[]::new),actor);
    }

    public String warehouseState(String warehouseId) { return tasks.warehouseState(warehouseId); }
    public void lockWarehouse(String warehouseId) { tasks.lockWarehouse("WMS:WH:" + warehouseId); }
    public int acquire(String warehouseId,String taskId) { return tasks.acquire(warehouseId,taskId); }
    public String lockOwner(String warehouseId) { return tasks.lockOwner(warehouseId); }
    public int release(String warehouseId,String taskId) { return tasks.release(warehouseId,taskId); }

    public List<Book> books(Task task) {
        return tasks.books(task.warehouseId(),task.locationIds().toArray(String[]::new),
                        task.materialIds().toArray(String[]::new))
                .stream().map(row -> new Book(dimension(row),row.getQuantity())).toList();
    }

    /** 快照行的数据库默认时间仍由 PostgreSQL 生成。 */
    public int snapshot(String id,String taskId,Book book,String actor) {
        InventoryCountLineEntity row = lineRow(id,taskId,book.dimension(),actor);
        row.setBookQuantity(book.quantity());
        return lines.insert(row);
    }

    public int transition(String id,String from,String to,String actor,String transactionId) {
        return switch (to) {
            case "COUNTING" -> tasks.start(id,from,actor,transactionId);
            case "CONFIRMED" -> tasks.confirm(id,from,actor,transactionId);
            case "COMPLETED" -> tasks.complete(id,from,actor,transactionId);
            case "CANCELLED" -> tasks.cancel(id,from,actor,transactionId);
            default -> throw new IllegalArgumentException("盘点目标状态非法");
        };
    }

    public int count(String taskId,String lineId,BigDecimal quantity,String actor) {
        return lines.recordCount(taskId,lineId,quantity,actor);
    }

    /** 账外行保存数据库时间，维持与旧 SQL 相同的触发器和审计语义。 */
    public int addExtra(String id,String taskId,Dimension d,BigDecimal quantity,String actor) {
        InventoryCountLineEntity row = lineRow(id,taskId,d,actor);
        row.setBookQuantity(BigDecimal.ZERO);
        row.setCountedQuantity(quantity);
        row.setCountedBy(actor);
        return lines.insertExtra(row);
    }

    public boolean snExists(String sn) { return Boolean.TRUE.equals(tasks.snExists(sn)); }
}
