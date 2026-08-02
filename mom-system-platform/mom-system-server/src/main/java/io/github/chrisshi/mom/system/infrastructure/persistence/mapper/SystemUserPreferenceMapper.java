package io.github.chrisshi.mom.system.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemUserPreferenceEntity;
import org.apache.ibatis.annotations.Mapper;

/** 用户显示偏好单表 Mapper；全部操作由 MyBatis-Plus BaseMapper/Wrapper 表达。 */
@Mapper
public interface SystemUserPreferenceMapper extends MomBaseMapper<SystemUserPreferenceEntity> {
}
