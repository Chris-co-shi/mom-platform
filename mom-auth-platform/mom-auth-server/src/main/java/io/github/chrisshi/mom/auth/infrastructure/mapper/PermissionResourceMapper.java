package io.github.chrisshi.mom.auth.infrastructure.mapper;

import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionResourceEntity;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Auth Permission Resource 单表数据访问入口。
 *
 * <p>仅提供 MyBatis-Plus 基础能力；查询、状态和引用规则留在 Application。
 * Mapper 无共享可变状态，数据库不可用时异常上抛。</p>
 */
@Mapper
public interface PermissionResourceMapper extends MomBaseMapper<PermissionResourceEntity> {
}
