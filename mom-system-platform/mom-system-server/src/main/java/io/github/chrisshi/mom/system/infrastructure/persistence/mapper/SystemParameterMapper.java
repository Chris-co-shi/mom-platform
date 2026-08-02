package io.github.chrisshi.mom.system.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemParameterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * System Parameter 的 MyBatis-Plus Mapper。
 *
 * <p>普通 CRUD、查询、计数、排序和分页全部由 {@link MomBaseMapper} 与 Wrapper 表达。唯一保留的固定
 * PostgreSQL 语句用于获取事务级 advisory lock，不维护 Mapper XML。</p>
 */
@Mapper
public interface SystemParameterMapper extends MomBaseMapper<SystemParameterEntity> {

    /** 获取同 Key 的事务级串行化锁；返回值无业务含义。 */
    @Select("SELECT pg_advisory_xact_lock(hashtextextended(#{parameterKey}, 0)) IS NULL")
    boolean lockParameterKey(@Param("parameterKey") String parameterKey);
}
