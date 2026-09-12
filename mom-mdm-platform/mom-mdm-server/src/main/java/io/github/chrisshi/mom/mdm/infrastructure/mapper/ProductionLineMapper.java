package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.ProductionLineEntity;
import org.apache.ibatis.annotations.Mapper;

/** ProductionLine 单表 Mapper；父级校验和事务编排不属于 Infrastructure。 */
@Mapper
public interface ProductionLineMapper extends MomBaseMapper<ProductionLineEntity> {
}
