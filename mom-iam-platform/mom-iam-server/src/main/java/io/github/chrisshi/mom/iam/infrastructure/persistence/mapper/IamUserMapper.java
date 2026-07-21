package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import org.apache.ibatis.annotations.Mapper;

/** IAM 用户受控 Mapper；Wrapper-only Update 由 MomBaseMapper 拒绝。 */
@Mapper
public interface IamUserMapper extends MomBaseMapper<IamUserEntity> {
}
