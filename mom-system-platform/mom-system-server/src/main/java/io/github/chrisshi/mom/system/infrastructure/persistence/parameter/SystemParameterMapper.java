package io.github.chrisshi.mom.system.infrastructure.persistence.parameter;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.api.ParameterScopeType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * System Parameter 的 MyBatis Mapper。
 *
 * <p>简单写入统一使用 MomBaseMapper 的实体路径以触发审计和乐观锁；动态分页查询使用参数化 SQL，禁止
 * 任意排序与任意字段过滤。同 Key 写事务使用 PostgreSQL 事务级 advisory lock，网络或数据库失败直接向上
 * 传播，锁在本地事务结束时自动释放。</p>
 */
@Mapper
public interface SystemParameterMapper extends MomBaseMapper<SystemParameterEntity> {

    /** 获取同 Key 的事务级串行化锁；返回值无业务含义。 */
    @Select("SELECT pg_advisory_xact_lock(hashtextextended(#{parameterKey}, 0)) IS NULL")
    boolean lockParameterKey(@Param("parameterKey") String parameterKey);

    /** 按参数键读取所有 Scope，固定按作用域与编码排序。 */
    @Select("""
            SELECT * FROM system_parameter
             WHERE parameter_key = #{parameterKey}
             ORDER BY scope_type, scope_code, id
            """)
    List<SystemParameterEntity> selectAllByKey(@Param("parameterKey") String parameterKey);

    /** 按 Scope 与 Key 精确读取唯一记录。 */
    @Select("""
            SELECT * FROM system_parameter
             WHERE scope_type = #{scopeType}
               AND scope_code = #{scopeCode}
               AND parameter_key = #{parameterKey}
            """)
    SystemParameterEntity selectByScopeAndKey(
            @Param("scopeType") ParameterScopeType scopeType,
            @Param("scopeCode") String scopeCode,
            @Param("parameterKey") String parameterKey);

    /** 执行有限条件和固定排序分页查询。 */
    List<SystemParameterEntity> selectPage(
            @Param("scopeType") ParameterScopeType scopeType,
            @Param("scopeCode") String scopeCode,
            @Param("parameterKey") String parameterKey,
            @Param("enabled") Boolean enabled,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /** 统计与分页条件完全一致的记录数。 */
    long countPage(
            @Param("scopeType") ParameterScopeType scopeType,
            @Param("scopeCode") String scopeCode,
            @Param("parameterKey") String parameterKey,
            @Param("enabled") Boolean enabled);
}
