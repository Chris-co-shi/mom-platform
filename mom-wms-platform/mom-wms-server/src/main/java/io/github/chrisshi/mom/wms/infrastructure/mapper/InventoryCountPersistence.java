package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** 盘点任务、整仓锁及账面快照的 WMS 本地持久化；写方法由 Application 事务统一调用。 */
@Repository
public class InventoryCountPersistence {
    private final InventoryCountSqlMapper mapper;

    /** @param mapper WMS 数据源绑定的普通 MyBatis Mapper */
    public InventoryCountPersistence(InventoryCountSqlMapper mapper) { this.mapper = mapper; }

    /** 盘点任务持久化快照。 */
    public record Task(String id, String taskNo, String requestKey, String warehouseId, String warehouseCode, List<String> locationIds,
                       List<String> materialIds, String status, String transactionId, String createdBy,
                       Instant createdAt, String updatedBy, Instant updatedAt, String startedBy, Instant startedAt,
                       String confirmedBy, Instant confirmedAt, String completedBy, Instant completedAt,
                       String cancelledBy, Instant cancelledAt, long version) { }
    /** 完整库存维度、账面快照和实盘事实。 */
    public record Line(String id, String taskId, Dimension dimension, BigDecimal bookQuantity,
                       BigDecimal countedQuantity, String snapshotBy, Instant snapshotAt,
                       String countedBy, Instant countedAt) {
        /** 已录入行的有符号盘点差异。 */
        public BigDecimal difference() { return countedQuantity.subtract(bookQuantity); }
    }
    /** 账面余额行；包含已有零余额。 */
    public record Book(Dimension dimension, BigDecimal quantity) { }

    private static String value(Map<String,Object> row,String column) { return (String) row.get(column); }
    private static Instant time(Map<String,Object> row,String column) {
        Object value=row.get(column);
        if(value==null) return null;
        if(value instanceof Instant instant) return instant;
        if(value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        return ((Date)value).toInstant();
    }
    private static List<String> strings(Map<String,Object> row,String column) {
        Object value=row.get(column);
        if(value==null) return List.of();
        return List.copyOf(Arrays.asList((String[])value));
    }
    private static Dimension dimension(Map<String,Object> row) {
        return new Dimension(value(row,"material_id"),value(row,"material_code"),value(row,"material_name"),
            value(row,"uom_code"),value(row,"batch_code"),value(row,"sn_code"),
            value(row,"warehouse_id"),value(row,"warehouse_code"),value(row,"location_id"),
            value(row,"location_code"),QualityStatus.valueOf(value(row,"quality_status")),
            WarehouseStatus.valueOf(value(row,"warehouse_status")));
    }
    private static Task task(Map<String,Object> row) {
        if(row==null) return null;
        return new Task(value(row,"id"),value(row,"task_no"),value(row,"request_key"),
            value(row,"warehouse_id"),value(row,"warehouse_code"),strings(row,"location_ids"),
            strings(row,"material_ids"),value(row,"status"),value(row,"transaction_id"),
            value(row,"created_by"),time(row,"created_at"),value(row,"updated_by"),time(row,"updated_at"),
            value(row,"started_by"),time(row,"started_at"),value(row,"confirmed_by"),time(row,"confirmed_at"),
            value(row,"completed_by"),time(row,"completed_at"),value(row,"cancelled_by"),time(row,"cancelled_at"),
            ((Number)row.get("version")).longValue());
    }
    private static Line line(Map<String,Object> row) {
        return new Line(value(row,"id"),value(row,"task_id"),dimension(row),
            (BigDecimal)row.get("book_quantity"),(BigDecimal)row.get("counted_quantity"),
            value(row,"snapshot_by"),time(row,"snapshot_at"),value(row,"counted_by"),time(row,"counted_at"));
    }

    /** 创建草稿及其经过 MDM 验证的范围。 */
    public void create(String id,String number,String requestKey,String warehouseId,String warehouseCode,
                       List<String> locations,List<String> materials,String actor) {
        mapper.create(id,number,requestKey,warehouseId,warehouseCode,
            locations.toArray(String[]::new),materials.toArray(String[]::new),actor);
    }
    /** 读取任务；不存在时返回 null。 */
    public Task find(String id) { return task(mapper.find(id)); }
    /** 创建请求键的事务级串行化锁，必须在发号前取得。 */
    public void lockCreateRequest(String requestKey) { mapper.lockCreateRequest("WMS:COUNT:CREATE:"+requestKey); }
    /** 查找已提交的同键创建结果。 */
    public Task findByRequestKey(String requestKey) { return task(mapper.findByRequestKey(requestKey)); }
    /** 锁定任务行以串行化状态及明细更改。 */
    public Task lockTask(String id) { return task(mapper.lockTask(id)); }
    /** 读取某任务的所有完整维度盘点行。 */
    public List<Line> lines(String taskId) { return mapper.lines(taskId).stream().map(InventoryCountPersistence::line).toList(); }
    /** 在已锁定任务行的事务内更新范围。 */
    public int updateScope(String id,List<String> locations,List<String> materials,String actor) {
        return mapper.updateScope(id,locations.toArray(String[]::new),materials.toArray(String[]::new),actor);
    }
    /** 查询 WMS 生命周期；盘点仅允许 ACTIVE 仓库。 */
    public String warehouseState(String warehouseId) { return mapper.warehouseState(warehouseId); }
    /** 按与记账一致的仓库键取得事务级 advisory lock。 */
    public void lockWarehouse(String warehouseId) { mapper.lockWarehouse("WMS:WH:"+warehouseId); }
    /** 在同一事务内建立整仓唯一持久锁；冲突时由主键拒绝。 */
    public int acquire(String warehouseId,String taskId) { return mapper.acquire(warehouseId,taskId); }
    /** 校验当前整仓锁所有权。 */
    public String lockOwner(String warehouseId) { return mapper.lockOwner(warehouseId); }
    /** 删除属于该任务的持久仓库锁。 */
    public int release(String warehouseId,String taskId) { return mapper.release(warehouseId,taskId); }
    /** 在整仓事务级锁下读取范围内账面余额。 */
    public List<Book> books(Task task) {
        return mapper.books(task.warehouseId(),task.locationIds().toArray(String[]::new),
            task.materialIds().toArray(String[]::new)).stream()
            .map(row -> new Book(dimension(row),(BigDecimal)row.get("quantity"))).toList();
    }
    /** 将一条余额连同实际技术 ID 固化为盘点快照。 */
    public int snapshot(String id,String taskId,Book book,String actor) {
        Dimension d=book.dimension();
        return mapper.snapshot(id,taskId,d.materialId(),d.materialCode(),d.materialName(),d.uomCode(),
            d.batchCode(),d.snCode(),d.warehouseId(),d.warehouseCode(),d.locationId(),d.locationCode(),
            d.qualityStatus().name(),d.warehouseStatus().name(),book.quantity(),actor);
    }
    /** 更新任务状态及操作审计时间。 */
    public int transition(String id,String from,String to,String actor,String transactionId) {
        return switch(to) {
            case "COUNTING" -> mapper.start(id,from,actor,transactionId);
            case "CONFIRMED" -> mapper.confirm(id,from,actor,transactionId);
            case "COMPLETED" -> mapper.complete(id,from,actor,transactionId);
            case "CANCELLED" -> mapper.cancel(id,from,actor,transactionId);
            default -> throw new IllegalArgumentException("盘点目标状态非法");
        };
    }
    /** 录入精确实盘数量；COUNTING 任务由调用方先锁定。 */
    public int count(String taskId,String lineId,BigDecimal quantity,String actor) {
        return mapper.count(taskId,lineId,quantity,actor);
    }
    /** 插入明确质量和仓储状态的账外实物行；唯一维度约束禁止重复。 */
    public int addExtra(String id,String taskId,Dimension d,BigDecimal quantity,String actor) {
        return mapper.addExtra(id,taskId,d.materialId(),d.materialCode(),d.materialName(),d.uomCode(),
            d.batchCode(),d.snCode(),d.warehouseId(),d.warehouseCode(),d.locationId(),d.locationCode(),
            d.qualityStatus().name(),d.warehouseStatus().name(),quantity,actor);
    }
    /** 检查 SN 在仓库中现有正余额位置，防止账外行凭空复制标签。 */
    public boolean snExists(String sn) { return Boolean.TRUE.equals(mapper.snExists(sn)); }
}
