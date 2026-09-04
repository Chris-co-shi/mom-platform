package io.github.chrisshi.mom.auth.infrastructure.mapper;

import io.github.chrisshi.mom.auth.infrastructure.entity.RoleEntity;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends MomBaseMapper<RoleEntity> {

    @Select("""
        SELECT id, code, name, description, enabled,
               created_at, created_by, updated_at, updated_by, version, deleted
        FROM auth_role
        WHERE deleted = false
        ORDER BY code ASC, id ASC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<RoleEntity> selectPage(@Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM auth_role WHERE deleted = false")
    long countActive();
}
