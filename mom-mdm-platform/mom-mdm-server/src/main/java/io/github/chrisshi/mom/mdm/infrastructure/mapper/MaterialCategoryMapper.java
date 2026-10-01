package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.MaterialCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MaterialCategory 单表数据访问入口。
 *
 * <p>该 Mapper 仅由 MDM Application 使用，依赖 MyBatis-Plus 完成普通 CRUD、条件分页和逐级父节点查询；
 * 不承载树规则、事务或 HTTP 语义。数据库异常保持失败并由 Application 事务回滚。</p>
 */
@Mapper
public interface MaterialCategoryMapper extends MomBaseMapper<MaterialCategoryEntity> {
    /**
     * 锁定有效物料分类；调用方必须处于本地事务中，数据库异常直接上抛。
     * @param id 分类 ID
     * @return 被行锁锁定的分类；不存在时返回 null
     */
    @Select("""
            SELECT id, code, name, parent_id, sort, default_shelf_life_days, status,
                   created_at, created_by, updated_at, updated_by, version, deleted
              FROM mdm_material_category
             WHERE id = #{id} AND deleted = false
             FOR UPDATE
            """)
    MaterialCategoryEntity selectByIdForUpdate(@Param("id") String id);

    /**
     * 按 ID 稳定顺序锁定根分类及后代，额外读取一行用于识别超过 V1 上限的子树。
     *
     * @param rootId 根分类 ID
     * @param limit 最大返回行数，调用方传入业务上限加一
     * @return 已锁定的有效分类列表
     */
    @Select("""
            WITH RECURSIVE subtree AS (
                SELECT id, ARRAY[id]::varchar(19)[] AS path
                  FROM mdm_material_category
                 WHERE id = #{rootId} AND deleted = false
                UNION ALL
                SELECT child.id, (parent.path || child.id)::varchar(19)[]
                  FROM mdm_material_category child
                  JOIN subtree parent ON child.parent_id = parent.id
                 WHERE child.deleted = false AND NOT child.id = ANY(parent.path)
            )
            SELECT c.id, c.code, c.name, c.parent_id, c.sort, c.default_shelf_life_days,
                   c.status, c.created_at, c.created_by, c.updated_at, c.updated_by,
                   c.version, c.deleted
              FROM mdm_material_category c
              JOIN subtree s ON s.id = c.id
             ORDER BY c.id
             LIMIT #{limit}
             FOR UPDATE OF c
            """)
    List<MaterialCategoryEntity> selectSubtreeForUpdate(
            @Param("rootId") String rootId, @Param("limit") int limit);
}
