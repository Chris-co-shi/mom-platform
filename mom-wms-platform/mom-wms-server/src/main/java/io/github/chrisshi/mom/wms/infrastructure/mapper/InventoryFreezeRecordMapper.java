package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryFreezeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 冻结事实单表读取：按交易号、库存维度等条件均使用 MyBatis-Plus LambdaQueryWrapper。
 * 写入与冻结剩余量原子递减只能通过 InventoryFreezeMapper 的受限操作。
 */
@Mapper
public interface InventoryFreezeRecordMapper extends MomBaseMapper<InventoryFreezeEntity> { }
