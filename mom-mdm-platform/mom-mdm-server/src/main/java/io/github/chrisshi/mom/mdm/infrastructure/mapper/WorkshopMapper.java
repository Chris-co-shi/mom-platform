package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WorkshopEntity;
import org.apache.ibatis.annotations.Mapper;

/** Workshop 单表 Mapper；不实现跨表 JOIN、物理外键或级联行为。 */
@Mapper
public interface WorkshopMapper extends MomBaseMapper<WorkshopEntity> {
}
