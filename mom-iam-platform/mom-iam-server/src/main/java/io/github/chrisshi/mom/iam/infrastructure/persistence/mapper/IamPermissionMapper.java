package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamPermissionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 系统 Permission 目录受控 Mapper。 */
@Mapper
public interface IamPermissionMapper extends MomBaseMapper<IamPermissionEntity> {
}
