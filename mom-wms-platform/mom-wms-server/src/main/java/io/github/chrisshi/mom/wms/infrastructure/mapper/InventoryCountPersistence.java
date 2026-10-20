package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.wms.application.InventoryAccountingService.Dimension;
import io.github.chrisshi.mom.wms.application.QualityStatus;
import io.github.chrisshi.mom.wms.application.WarehouseStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/** 盘点任务、整仓锁及不可变账面快照的 WMS 本地 SQL；写方法由 Application 事务统一调用。 */
@Repository
public class InventoryCountPersistence {
    private final JdbcTemplate jdbc;

    /** @param jdbc 与库存记账相同的 WMS 数据源 */
    public InventoryCountPersistence(JdbcTemplate jdbc) { this.jdbc = jdbc; }

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

    private static List<String> strings(Array value) throws SQLException {
        return value == null ? List.of() : List.of((String[]) value.getArray());
    }
    private static Instant time(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static final RowMapper<Task> TASK = (rs, row) -> new Task(
        rs.getString("id"), rs.getString("task_no"), rs.getString("request_key"), rs.getString("warehouse_id"), rs.getString("warehouse_code"),
        strings(rs.getArray("location_ids")), strings(rs.getArray("material_ids")), rs.getString("status"),
        rs.getString("transaction_id"), rs.getString("created_by"), time(rs,"created_at"),
        rs.getString("updated_by"), time(rs,"updated_at"), rs.getString("started_by"), time(rs,"started_at"),
        rs.getString("confirmed_by"), time(rs,"confirmed_at"), rs.getString("completed_by"), time(rs,"completed_at"),
        rs.getString("cancelled_by"), time(rs,"cancelled_at"), rs.getLong("version"));
    private static final RowMapper<Line> LINE = (rs, row) -> new Line(
        rs.getString("id"), rs.getString("task_id"),
        new Dimension(rs.getString("material_id"), rs.getString("material_code"), rs.getString("material_name"),
            rs.getString("uom_code"), rs.getString("batch_code"), rs.getString("sn_code"),
            rs.getString("warehouse_id"), rs.getString("warehouse_code"), rs.getString("location_id"),
            rs.getString("location_code"), QualityStatus.valueOf(rs.getString("quality_status")),
            WarehouseStatus.valueOf(rs.getString("warehouse_status"))),
        rs.getBigDecimal("book_quantity"), rs.getBigDecimal("counted_quantity"),
        rs.getString("snapshot_by"), time(rs,"snapshot_at"), rs.getString("counted_by"), time(rs,"counted_at"));

    /** 创建草稿及其经过 MDM 验证的范围。 */
    public void create(String id, String number, String requestKey, String warehouseId, String warehouseCode,
                       List<String> locations, List<String> materials, String actor) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO wms_inventory_count_task
                  (id,task_no,request_key,warehouse_id,warehouse_code,location_ids,material_ids,created_by,updated_by)
                VALUES (?,?,?,?,?,?,?,?,?)
                """);
            ps.setString(1,id); ps.setString(2,number); ps.setString(3,requestKey);
            ps.setString(4,warehouseId); ps.setString(5,warehouseCode);
            ps.setArray(6,connection.createArrayOf("varchar",locations.toArray(String[]::new)));
            ps.setArray(7,connection.createArrayOf("varchar",materials.toArray(String[]::new)));
            ps.setString(8,actor); ps.setString(9,actor);
            return ps;
        });
    }

    /** 读取任务；不存在时返回 null。 */
    public Task find(String id) {
        return jdbc.query("SELECT * FROM wms_inventory_count_task WHERE id=?", TASK, id).stream().findFirst().orElse(null);
    }
    /** 创建请求键的事务级串行化锁，必须在发号前取得。 */
    public void lockCreateRequest(String requestKey) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,
            "WMS:COUNT:CREATE:"+requestKey);
    }
    /** 查找已提交的同键创建结果。 */
    public Task findByRequestKey(String requestKey) {
        return jdbc.query("SELECT * FROM wms_inventory_count_task WHERE request_key=?",TASK,requestKey)
            .stream().findFirst().orElse(null);
    }
    /** 锁定任务行以串行化状态及明细更改。 */
    public Task lockTask(String id) {
        return jdbc.query("SELECT * FROM wms_inventory_count_task WHERE id=? FOR UPDATE", TASK, id).stream().findFirst().orElse(null);
    }
    /** 读取某任务的所有完整维度盘点行。 */
    public List<Line> lines(String taskId) {
        return jdbc.query("SELECT * FROM wms_inventory_count_line WHERE task_id=? ORDER BY id", LINE, taskId);
    }
    /** 在已锁定任务行的事务内更新范围。 */
    public int updateScope(String id, List<String> locations, List<String> materials, String actor) {
        return jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                UPDATE wms_inventory_count_task SET location_ids=?,material_ids=?,updated_by=?,updated_at=now(),version=version+1
                WHERE id=? AND status='DRAFT'
                """);
            ps.setArray(1,connection.createArrayOf("varchar",locations.toArray(String[]::new)));
            ps.setArray(2,connection.createArrayOf("varchar",materials.toArray(String[]::new)));
            ps.setString(3,actor); ps.setString(4,id); return ps;
        });
    }
    /** 查询 WMS 生命周期；盘点仅允许 ACTIVE 仓库。 */
    public String warehouseState(String warehouseId) {
        return jdbc.query("SELECT state FROM wms_warehouse_inventory_state WHERE warehouse_id=?",
            (rs,row) -> rs.getString(1),warehouseId).stream().findFirst().orElse(null);
    }
    /** 按与记账一致的仓库键取得事务级 advisory lock。 */
    public void lockWarehouse(String warehouseId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,"WMS:WH:"+warehouseId);
    }
    /** 在同一事务内建立整仓唯一持久锁；冲突时由主键拒绝。 */
    public int acquire(String warehouseId, String taskId) {
        return jdbc.update("INSERT INTO wms_inventory_count_scope_lock(warehouse_id,task_id) VALUES (?,?) ON CONFLICT (warehouse_id) DO NOTHING",warehouseId,taskId);
    }
    /** 校验当前整仓锁所有权。 */
    public String lockOwner(String warehouseId) {
        return jdbc.query("SELECT task_id FROM wms_inventory_count_scope_lock WHERE warehouse_id=?",
            (rs,row) -> rs.getString(1),warehouseId).stream().findFirst().orElse(null);
    }
    /** 删除属于该任务的持久仓库锁。 */
    public int release(String warehouseId, String taskId) {
        return jdbc.update("DELETE FROM wms_inventory_count_scope_lock WHERE warehouse_id=? AND task_id=?",warehouseId,taskId);
    }
    /** 账面余额行；包含已有零余额。 */
    public record Book(Dimension dimension, BigDecimal quantity) { }
    /** 在整仓事务级锁下读取范围内账面余额。 */
    public List<Book> books(Task task) {
        return jdbc.query(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                SELECT * FROM wms_inventory_balance b
                WHERE b.warehouse_id=? AND (cardinality(?::varchar[]) = 0 OR b.location_id=ANY(?::varchar[]))
                  AND (cardinality(?::varchar[]) = 0 OR b.material_id=ANY(?::varchar[]))
                ORDER BY b.id
                """);
            Array loc=connection.createArrayOf("varchar",task.locationIds().toArray(String[]::new));
            Array mat=connection.createArrayOf("varchar",task.materialIds().toArray(String[]::new));
            ps.setString(1,task.warehouseId()); ps.setArray(2,loc); ps.setArray(3,loc);
            ps.setArray(4,mat); ps.setArray(5,mat); return ps;
        },(rs,row) -> new Book(new Dimension(rs.getString("material_id"),rs.getString("material_code"),
            rs.getString("material_name"),rs.getString("uom_code"),rs.getString("batch_code"),
            rs.getString("sn_code"),rs.getString("warehouse_id"),rs.getString("warehouse_code"),
            rs.getString("location_id"),rs.getString("location_code"),
            QualityStatus.valueOf(rs.getString("quality_status")),
            WarehouseStatus.valueOf(rs.getString("warehouse_status"))),rs.getBigDecimal("quantity")));
    }
    /** 将一条余额连同实际技术 ID 固化为盘点快照。 */
    public int snapshot(String id,String taskId,Book book,String actor) {
        Dimension d=book.dimension();
        return jdbc.update("""
            INSERT INTO wms_inventory_count_line
              (id,task_id,material_id,material_code,material_name,uom_code,batch_code,sn_code,warehouse_id,
               warehouse_code,location_id,location_code,quality_status,warehouse_status,book_quantity,snapshot_by)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,taskId,d.materialId(),d.materialCode(),d.materialName(),d.uomCode(),d.batchCode(),d.snCode(),
            d.warehouseId(),d.warehouseCode(),d.locationId(),d.locationCode(),d.qualityStatus().name(),
            d.warehouseStatus().name(),book.quantity(),actor);
    }
    /** 更新任务状态及操作审计时间。 */
    public int transition(String id, String from, String to, String actor, String transactionId) {
        String auditColumns=switch(to) {
            case "COUNTING" -> "started_by=?,started_at=now()";
            case "CONFIRMED" -> "confirmed_by=?,confirmed_at=now()";
            case "COMPLETED" -> "completed_by=?,completed_at=now()";
            case "CANCELLED" -> "cancelled_by=?,cancelled_at=now()";
            default -> throw new IllegalArgumentException("盘点目标状态非法");
        };
        String sql="UPDATE wms_inventory_count_task SET status=?,updated_by=?,updated_at=now(),"+auditColumns+
            ",transaction_id=COALESCE(?,transaction_id),version=version+1 WHERE id=? AND status=?";
        return jdbc.update(sql,to,actor,actor,transactionId,id,from);
    }
    /** 录入精确实盘数量；COUNTING 任务由调用方先锁定。 */
    public int count(String taskId,String lineId,BigDecimal quantity,String actor) {
        return jdbc.update("UPDATE wms_inventory_count_line SET counted_quantity=?,counted_by=?,counted_at=now() WHERE id=? AND task_id=?",
            quantity,actor,lineId,taskId);
    }
    /** 插入明确质量和仓储状态的账外实物行；唯一维度约束禁止重复。 */
    public int addExtra(String id,String taskId,Dimension d,BigDecimal quantity,String actor) {
        return jdbc.update("""
            INSERT INTO wms_inventory_count_line
              (id,task_id,material_id,material_code,material_name,uom_code,batch_code,sn_code,warehouse_id,
               warehouse_code,location_id,location_code,quality_status,warehouse_status,book_quantity,
               counted_quantity,snapshot_by,counted_by,counted_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?,?,now())
            """,id,taskId,d.materialId(),d.materialCode(),d.materialName(),d.uomCode(),d.batchCode(),d.snCode(),
            d.warehouseId(),d.warehouseCode(),d.locationId(),d.locationCode(),d.qualityStatus().name(),
            d.warehouseStatus().name(),quantity,actor,actor);
    }
    /** 检查 SN 在仓库中现有正余额位置，防止账外行凭空复制标签。 */
    public boolean snExists(String sn) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM wms_inventory_balance WHERE sn_code=? AND quantity>0)",Boolean.class,sn));
    }
}
