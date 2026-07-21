package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 用户授权 Session 受控 Mapper。 */
@Mapper
public interface IamUserSessionMapper extends MomBaseMapper<IamUserSessionEntity> {
}
