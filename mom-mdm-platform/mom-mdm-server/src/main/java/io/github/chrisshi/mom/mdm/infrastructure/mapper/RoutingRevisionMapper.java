package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.RoutingRevisionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 路线修订单表 Mapper；状态转换与步骤替换先锁定修订行。 */
@Mapper
public interface RoutingRevisionMapper extends MomBaseMapper<RoutingRevisionEntity> {
    /** 锁定一个修订，保证同一修订的并发发布和修改串行化。 */
    @Select("""
            SELECT id, routing_id, revision_no, status, lock_version, released_at, obsolete_at,
                   created_at, created_by, updated_at, updated_by
              FROM mdm_routing_revision WHERE id=#{id} FOR UPDATE
            """)
    RoutingRevisionEntity selectByIdForUpdate(@Param("id") String id);
}
