package io.github.chrisshi.mom.system.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemNavigationItemEntity;
import org.apache.ibatis.annotations.Mapper;

/** System Navigation Draft 单表 Mapper；不声明重复 CRUD 或树递归 SQL。 */
@Mapper
public interface SystemNavigationItemMapper extends MomBaseMapper<SystemNavigationItemEntity> {
}
