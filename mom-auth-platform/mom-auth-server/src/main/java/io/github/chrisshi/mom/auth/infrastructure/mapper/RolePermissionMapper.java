package io.github.chrisshi.mom.auth.infrastructure.mapper;

import io.github.chrisshi.mom.auth.infrastructure.entity.RolePermissionEntity;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RolePermissionMapper extends MomBaseMapper<RolePermissionEntity> {
}
