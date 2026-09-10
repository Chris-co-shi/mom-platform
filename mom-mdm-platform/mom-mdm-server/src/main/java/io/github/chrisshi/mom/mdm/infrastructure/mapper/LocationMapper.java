package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.LocationEntity;
import org.apache.ibatis.annotations.Mapper;

/** Location 单表 Mapper；只保存可寻址位置主数据，不保存任何占用或预留事实。 */
@Mapper
public interface LocationMapper extends MomBaseMapper<LocationEntity> {
}
