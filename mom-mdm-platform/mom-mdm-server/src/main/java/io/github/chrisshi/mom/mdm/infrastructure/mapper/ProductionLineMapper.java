package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.ProductionLineEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** ProductionLine 单表 Mapper；普通 CRUD 复用 MyBatis-Plus，父子并发由事务内主键行锁串行化。 */
@Mapper
public interface ProductionLineMapper extends MomBaseMapper<ProductionLineEntity> {

    /**
     * 锁定有效 ProductionLine，供自身停用及 Workstation 创建、启用共同使用。
     * @param id ProductionLine ID
     * @return 已锁定 ProductionLine；不存在时返回 null
     */
    @Select("""
            SELECT id, code, name, workshop_id, status,
                   created_at, created_by, updated_at, updated_by, version, deleted
              FROM mdm_production_line
             WHERE id = #{id} AND deleted = false
             FOR UPDATE
            """)
    ProductionLineEntity selectByIdForUpdate(@Param("id") String id);
}
