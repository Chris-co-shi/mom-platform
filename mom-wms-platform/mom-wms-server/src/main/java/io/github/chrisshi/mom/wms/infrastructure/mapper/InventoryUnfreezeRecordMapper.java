package io.github.chrisshi.mom.wms.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.wms.infrastructure.entity.InventoryUnfreezeEntity;
import org.apache.ibatis.annotations.Mapper;

/** 解冻事实单表查询复用 mom-data；历史记录的不可变性由数据库 Trigger 保护。 */
@Mapper
public interface InventoryUnfreezeRecordMapper extends MomBaseMapper<InventoryUnfreezeEntity> { }
