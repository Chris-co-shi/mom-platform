package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomCategoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计量单位类别单表数据访问入口，属于 MDM Infrastructure 数据库适配边界。
 *
 * <p>仅复用 MyBatis-Plus CRUD/分页，不跨表编排基准单位；引用规则、事务和并发冲突由 Application 与
 * PostgreSQL 约束负责。数据库不可用时保留异常并触发上层事务回滚。</p>
 */
@Mapper
public interface UomCategoryMapper extends MomBaseMapper<UomCategoryEntity> {
}
