package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.RoutingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Routing 身份单表 Mapper；主行锁串行分配业务修订号。 */
@Mapper
public interface RoutingMapper extends MomBaseMapper<RoutingEntity> {
    /** 锁定路线身份，下一修订号只能由持锁事务分配。 */
    @Select("""
            SELECT id, code, name, plant_id, created_at, created_by, updated_at, updated_by
              FROM mdm_routing WHERE id=#{id} FOR UPDATE
            """)
    RoutingEntity selectByIdForUpdate(@Param("id") String id);
}
