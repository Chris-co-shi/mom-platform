package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Dynamic I18n Message 的 MyBatis-Plus Mapper。
 *
 * <p>只继承 MomBaseMapper；所有生产查询与写入由 Repository 使用类型安全 Wrapper 和 BaseMapper 方法表达，
 * 不声明重复 Mapper 方法，也不维护 Mapper XML。</p>
 */
@Mapper
public interface SystemI18nMessageMapper extends MomBaseMapper<SystemI18nMessageEntity> {
}
