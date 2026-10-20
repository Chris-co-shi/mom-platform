package io.github.chrisshi.mom.wms.infrastructure.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

/** 盘点任务列表的服务端分页查询。 */
@Mapper
public interface InventoryCountQueryMapper {
    /** 盘点任务列表摘要。 */
    record Summary(String id,String taskNo,String warehouseId,String warehouseCode,String status,
                   String transactionId,Instant createdAt,Instant startedAt,Instant completedAt) { }
    /**
     * 按仓库可选过滤，最近创建的任务优先。
     * @param page MyBatis-Plus 分页对象
     * @param warehouseId 可选的仓库稳定 ID
     * @return 任务摘要分页
     */
    @Select("<script>SELECT id,task_no AS taskNo,warehouse_id AS warehouseId,warehouse_code AS warehouseCode,status,transaction_id AS transactionId,created_at AS createdAt,started_at AS startedAt,completed_at AS completedAt FROM wms_inventory_count_task <if test='warehouseId != null'>WHERE warehouse_id=#{warehouseId}</if> ORDER BY created_at DESC,id DESC</script>")
    IPage<Summary> list(IPage<Summary> page,@Param("warehouseId") String warehouseId);
}
