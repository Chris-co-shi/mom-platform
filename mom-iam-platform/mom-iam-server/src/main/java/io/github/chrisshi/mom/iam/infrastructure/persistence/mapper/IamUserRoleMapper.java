package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserRoleEntity;
import org.apache.ibatis.annotations.Mapper;

/** 用户角色关系受控 Mapper。 */
@Mapper
public interface IamUserRoleMapper extends MomBaseMapper<IamUserRoleEntity> {
}
