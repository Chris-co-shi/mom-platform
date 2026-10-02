package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WarehouseAreaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** WarehouseArea 单表 Mapper；普通 CRUD 复用 MyBatis-Plus，位置父子并发由事务内主键行锁串行化。 */
@Mapper
public interface WarehouseAreaMapper extends MomBaseMapper<WarehouseAreaEntity> {

    /**
     * 锁定有效 WarehouseArea，供自身停用及 Location 创建、启用共同使用。
     * @param id WarehouseArea ID
     * @return 已锁定 WarehouseArea；不存在时返回 null
     */
    @Select("""
            SELECT id, code, name, warehouse_id, status,
                   created_at, created_by, updated_at, updated_by, version, deleted
              FROM mdm_warehouse_area
             WHERE id = #{id} AND deleted = false
             FOR UPDATE
            """)
    WarehouseAreaEntity selectByIdForUpdate(@Param("id") String id);
}
