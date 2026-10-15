package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.FormulaItemEntity;
import org.apache.ibatis.annotations.Mapper;

/** 配方明细单表 Mapper；只有 DRAFT 修订允许在 Application 中替换明细。 */
@Mapper
public interface FormulaItemMapper extends MomBaseMapper<FormulaItemEntity> {
}
