package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.DimensionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 量纲单表数据访问入口，属于 MDM Infrastructure 数据库适配边界。
 *
 * <p>仅由 Application 调用并复用 MyBatis-Plus CRUD/分页，不承载事务、换算算法或 HTTP 语义；并发与
 * 一致性分别由 Application 本地事务、BaseEntity 乐观锁和 PostgreSQL 约束负责，数据库不可用时直接失败。</p>
 */
@Mapper
public interface DimensionMapper extends MomBaseMapper<DimensionEntity> {
    /**
     * 锁定有效量纲行；调用方必须处于本地事务中，数据库异常直接上抛。
     * @param id 量纲 ID
     * @return 被行锁锁定的有效量纲；不存在时返回 null
     */
    @Select("""
            SELECT id, code, name, time_exponent, length_exponent, mass_exponent,
                   electric_current_exponent, temperature_exponent, amount_exponent,
                   luminous_intensity_exponent, status, created_at, created_by, updated_at,
                   updated_by, version, deleted
              FROM mdm_dimension
             WHERE id = #{id} AND deleted = false
             FOR UPDATE
            """)
    DimensionEntity selectByIdForUpdate(@Param("id") String id);
}
