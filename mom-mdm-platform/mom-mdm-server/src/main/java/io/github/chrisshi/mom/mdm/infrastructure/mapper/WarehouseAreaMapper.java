package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WarehouseAreaEntity;
import org.apache.ibatis.annotations.Mapper;

/** WarehouseArea 单表 Mapper；父仓库完整性由 Application 明确校验。 */
@Mapper
public interface WarehouseAreaMapper extends MomBaseMapper<WarehouseAreaEntity> {
}
