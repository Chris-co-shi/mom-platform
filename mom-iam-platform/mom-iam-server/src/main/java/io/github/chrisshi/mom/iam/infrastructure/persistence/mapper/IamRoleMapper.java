package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRoleEntity;
import org.apache.ibatis.annotations.Mapper;

/** IAM 角色目录受控 Mapper。 */
@Mapper
public interface IamRoleMapper extends MomBaseMapper<IamRoleEntity> {
}
