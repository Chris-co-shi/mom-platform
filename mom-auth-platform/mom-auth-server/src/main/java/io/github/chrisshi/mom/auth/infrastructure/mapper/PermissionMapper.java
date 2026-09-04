package io.github.chrisshi.mom.auth.infrastructure.mapper;

import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionEntity;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper extends MomBaseMapper<PermissionEntity> {

    @Select("""
        SELECT id, code, name, description, enabled,
               created_at, created_by, updated_at, updated_by, version, deleted
        FROM auth_permission
        WHERE deleted = false
        ORDER BY code ASC, id ASC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<PermissionEntity> selectPage(@Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM auth_permission WHERE deleted = false")
    long countActive();
}
