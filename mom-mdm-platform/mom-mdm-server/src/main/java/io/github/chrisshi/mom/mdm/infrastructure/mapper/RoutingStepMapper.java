package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.RoutingStepEntity;
import org.apache.ibatis.annotations.Mapper;

/** 路线步骤单表 Mapper；只有草稿修订允许应用层替换步骤。 */
@Mapper
public interface RoutingStepMapper extends MomBaseMapper<RoutingStepEntity> { }
