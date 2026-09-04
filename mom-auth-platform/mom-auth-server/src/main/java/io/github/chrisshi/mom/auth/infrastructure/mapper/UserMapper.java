package io.github.chrisshi.mom.auth.infrastructure.mapper;

import io.github.chrisshi.mom.auth.infrastructure.entity.UserEntity;
import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends MomBaseMapper<UserEntity> {

    @Select("""
        SELECT id, username, password_hash, display_name, enabled,
               created_at, created_by, updated_at, updated_by, version, deleted
        FROM auth_user
        WHERE deleted = false
        ORDER BY username ASC, id ASC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<UserEntity> selectPage(@Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM auth_user WHERE deleted = false")
    long countActive();
}
