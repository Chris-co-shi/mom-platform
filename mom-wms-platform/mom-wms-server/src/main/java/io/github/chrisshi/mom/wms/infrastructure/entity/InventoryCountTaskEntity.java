package io.github.chrisshi.mom.wms.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.ArrayTypeHandler;

import java.time.Instant;

/**
 * 盘点任务持久化实体。审计继承 mom-data，生命周期与版本仍由盘点用例的条件 SQL 控制。
 * 不使用逻辑删除：已完成和已取消的任务必须保留追溯事实。
 */
@Getter
@Setter
@TableName(value = "wms_inventory_count_task", autoResultMap = true)
public class InventoryCountTaskEntity extends BaseAuditEntity {
    private String taskNo;
    private String requestKey;
    private String warehouseId;
    private String warehouseCode;

    /** PostgreSQL varchar[]；空数组表示整个仓库或全部物料。 */
    @TableField(typeHandler = ArrayTypeHandler.class)
    private String[] locationIds;
    @TableField(typeHandler = ArrayTypeHandler.class)
    private String[] materialIds;

    private String status = "DRAFT";
    private String transactionId;
    private String startedBy;
    private Instant startedAt;
    private String confirmedBy;
    private Instant confirmedAt;
    private String completedBy;
    private Instant completedAt;
    private String cancelledBy;
    private Instant cancelledAt;

    /** 条件状态迁移通过 SQL 显式推进，不由通用 updateById 静默替换。 */
    private Long version = 0L;
}
