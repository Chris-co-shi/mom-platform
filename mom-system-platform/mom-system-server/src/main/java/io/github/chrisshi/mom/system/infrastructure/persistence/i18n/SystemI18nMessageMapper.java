package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Dynamic I18n Draft Message 的 MyBatis-Plus Mapper。
 *
 * <p>普通 CRUD、父资源限定、启用列表、计数、排序和分页统一使用
 * {@code MomBaseMapper + LambdaQueryWrapper}，不为 MyBatis-Plus 已支持的单表查询维护 XML。</p>
 */
@Mapper
public interface SystemI18nMessageMapper extends MomBaseMapper<SystemI18nMessageEntity> {
}
