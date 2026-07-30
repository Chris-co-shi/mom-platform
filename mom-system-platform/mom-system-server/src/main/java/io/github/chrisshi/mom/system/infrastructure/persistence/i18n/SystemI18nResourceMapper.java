package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Dynamic I18n Resource 的 MyBatis-Plus Mapper。
 *
 * <p>普通 CRUD、条件查询、计数、排序和分页全部由 Repository 通过
 * {@code MomBaseMapper + LambdaQueryWrapper} 表达，不为单表等值条件重复创建 XML SQL。</p>
 */
@Mapper
public interface SystemI18nResourceMapper extends MomBaseMapper<SystemI18nResourceEntity> {
}
