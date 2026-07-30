package io.github.chrisshi.mom.system.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemI18nResourceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Dynamic I18n Resource 的 MyBatis-Plus Mapper。
 *
 * <p>只继承 MomBaseMapper；所有生产查询与写入由 Repository 使用类型安全 Wrapper 和 BaseMapper 方法表达，
 * 不声明重复 Mapper 方法，也不维护 Mapper XML。</p>
 */
@Mapper
public interface SystemI18nResourceMapper extends MomBaseMapper<SystemI18nResourceEntity> {
}
