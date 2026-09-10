package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WorkstationEntity;
import org.apache.ibatis.annotations.Mapper;

/** Workstation 单表 Mapper；复用 MomBaseMapper 的受治理更新规则。 */
@Mapper
public interface WorkstationMapper extends MomBaseMapper<WorkstationEntity> {
}
