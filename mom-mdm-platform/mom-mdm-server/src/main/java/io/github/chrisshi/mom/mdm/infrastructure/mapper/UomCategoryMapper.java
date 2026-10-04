package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 计量单位类别单表数据访问入口，属于 MDM Infrastructure 数据库适配边界。
 *
 * <p>仅复用 MyBatis-Plus CRUD/分页，不跨表编排基准单位；引用规则、事务和并发冲突由 Application 与
 * PostgreSQL 约束负责。数据库不可用时保留异常并触发上层事务回滚。</p>
 */
@Mapper
public interface UomCategoryMapper extends MomBaseMapper<UomCategoryEntity> {
    /**
     * 锁定有效计量单位类别；调用方必须处于本地事务中，数据库异常直接上抛。
     * @param id 类别 ID
     * @return 被行锁锁定的类别；不存在时返回 null
     */
    @Select("""
            SELECT id, code, name, dimension_id, status, created_at, created_by,
                   updated_at, updated_by, version, deleted
              FROM mdm_uom_category
             WHERE id = #{id} AND deleted = false
             FOR UPDATE
            """)
    UomCategoryEntity selectByIdForUpdate(@Param("id") String id);
}
