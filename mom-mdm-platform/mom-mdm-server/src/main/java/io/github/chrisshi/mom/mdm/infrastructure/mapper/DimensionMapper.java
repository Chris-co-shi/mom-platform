package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.DimensionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 量纲单表数据访问入口，属于 MDM Infrastructure 数据库适配边界。
 *
 * <p>仅由 Application 调用并复用 MyBatis-Plus CRUD/分页，不承载事务、换算算法或 HTTP 语义；并发与
 * 一致性分别由 Application 本地事务、BaseEntity 乐观锁和 PostgreSQL 约束负责，数据库不可用时直接失败。</p>
 */
@Mapper
public interface DimensionMapper extends MomBaseMapper<DimensionEntity> {
}
