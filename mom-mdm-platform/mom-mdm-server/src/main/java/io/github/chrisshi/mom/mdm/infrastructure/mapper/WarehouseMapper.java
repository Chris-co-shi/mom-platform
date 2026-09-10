package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WarehouseEntity;
import org.apache.ibatis.annotations.Mapper;

/** Warehouse 单表 Mapper；不访问库存、容器或其他 bounded context。 */
@Mapper
public interface WarehouseMapper extends MomBaseMapper<WarehouseEntity> {
}
