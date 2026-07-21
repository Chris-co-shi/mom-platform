package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserFactoryScopeEntity;
import org.apache.ibatis.annotations.Mapper;

/** 用户 Factory Scope 受控 Mapper。 */
@Mapper
public interface IamUserFactoryScopeMapper extends MomBaseMapper<IamUserFactoryScopeEntity> {
}
