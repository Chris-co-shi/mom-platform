package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WorkstationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Workstation 单表 Mapper；复用 MomBaseMapper 的受治理更新规则。 */
@Mapper
public interface WorkstationMapper extends MomBaseMapper<WorkstationEntity> {
    /** 制造定义发布时锁定工位，避免校验后并发停用。 */
    @Select("""
            SELECT id, code, name, production_line_id, status,
                   created_at, created_by, updated_at, updated_by, version, deleted
              FROM mdm_workstation WHERE id=#{id} AND deleted=false FOR UPDATE
            """)
    WorkstationEntity selectByIdForUpdate(@Param("id") String id);
}
