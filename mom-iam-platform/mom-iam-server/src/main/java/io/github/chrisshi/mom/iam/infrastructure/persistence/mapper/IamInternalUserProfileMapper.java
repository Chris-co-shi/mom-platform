package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamInternalUserProfileEntity;
import org.apache.ibatis.annotations.Mapper;

/** INTERNAL 用户资料受控 Mapper。 */
@Mapper
public interface IamInternalUserProfileMapper extends MomBaseMapper<IamInternalUserProfileEntity> {
}
