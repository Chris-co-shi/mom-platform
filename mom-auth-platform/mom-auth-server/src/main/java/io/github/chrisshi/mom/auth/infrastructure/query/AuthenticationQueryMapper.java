package io.github.chrisshi.mom.auth.infrastructure.query;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthenticationQueryMapper {

    List<String> selectAuthoritiesByUserId(@Param("userId") String userId);
}
