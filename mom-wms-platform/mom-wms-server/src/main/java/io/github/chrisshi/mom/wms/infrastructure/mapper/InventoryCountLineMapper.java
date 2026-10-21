package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryCountLineEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 盘点明细的受限持久化：普通读写复用 MomBaseMapper，实盘数量仅以受限 SQL 修改；
 * 数据库触发器继续阻止快照变化和非法生命周期写入。
 */
@Mapper
public interface InventoryCountLineMapper extends MomBaseMapper<InventoryCountLineEntity> {
    @Update("UPDATE wms_inventory_count_line SET counted_quantity=#{quantity},counted_by=#{actor},counted_at=now() WHERE id=#{lineId} AND task_id=#{taskId}")
    int recordCount(@Param("taskId") String taskId,@Param("lineId") String lineId,
                    @Param("quantity") BigDecimal quantity,@Param("actor") String actor);
}
