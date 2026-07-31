package io.github.chrisshi.mom.system.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemApplicationEntity;
import org.apache.ibatis.annotations.Mapper;

/** System Application 单表 Mapper；普通 CRUD 全部复用 MyBatis-Plus。 */
@Mapper
public interface SystemApplicationMapper extends MomBaseMapper<SystemApplicationEntity> {
}
