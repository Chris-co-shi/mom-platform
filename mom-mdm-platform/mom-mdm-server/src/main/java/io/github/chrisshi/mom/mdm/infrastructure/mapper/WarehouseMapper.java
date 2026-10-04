package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WarehouseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Warehouse 单表 Mapper；普通 CRUD 复用 MyBatis-Plus，父子并发由事务内主键行锁串行化。 */
@Mapper
public interface WarehouseMapper extends MomBaseMapper<WarehouseEntity> {

    /**
     * 锁定有效 Warehouse，供自身停用及 WarehouseArea 创建、启用共同使用。
     * @param id Warehouse ID
     * @return 已锁定 Warehouse；不存在时返回 null
     */
    @Select("""
            SELECT id, code, name, plant_id, status, created_at, created_by, updated_at, updated_by, version, deleted
              FROM mdm_warehouse
             WHERE id = #{id} AND deleted = false
             FOR UPDATE
            """)
    WarehouseEntity selectByIdForUpdate(@Param("id") String id);
}
