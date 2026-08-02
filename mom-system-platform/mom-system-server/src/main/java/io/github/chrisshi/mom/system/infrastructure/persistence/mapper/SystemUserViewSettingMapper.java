package io.github.chrisshi.mom.system.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemUserViewSettingEntity;
import org.apache.ibatis.annotations.Mapper;

/** 用户视图设置单表 Mapper；不声明 XML、注解 SQL 或重复 CRUD 方法。 */
@Mapper
public interface SystemUserViewSettingMapper extends MomBaseMapper<SystemUserViewSettingEntity> {
}
