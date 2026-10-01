package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.MaterialEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;

/**
 * Material 单表访问与引用保护查询入口。
 *
 * <p>普通 CRUD 和分页复用 MyBatis-Plus；这里的固定 SQL 只用于锁定目标物料以及判断非删除物料是否
 * 引用分类或单位链。所有参数均由 MyBatis 绑定，Mapper 不承担业务规则、事务或 HTTP 语义。数据库
 * 不可用时异常上抛，由 Application 事务 fail-closed。</p>
 */
@Mapper
public interface MaterialMapper extends MomBaseMapper<MaterialEntity> {

    /**
     * 锁定一条有效物料，供更新和状态切换串行化。
     *
     * @param id 物料 ID
     * @return 被锁定的物料；不存在或已逻辑删除时返回 null
     */
    @Select("""
            SELECT id, code, name, category_id, base_uom_id, shelf_life_days, status,
                   created_at, created_by, updated_at, updated_by, version, deleted
              FROM mdm_material
             WHERE id = #{id} AND deleted = false
             FOR UPDATE
            """)
    MaterialEntity selectByIdForUpdate(@Param("id") String id);

    /**
     * 判断任一分类是否仍被非删除物料引用；查询无写副作用。
     * @param categoryIds 非空分类 ID 集合
     * @return 存在至少一条引用时返回 true
     */
    @Select("""
            <script>
            SELECT EXISTS (
                SELECT 1 FROM mdm_material
                 WHERE deleted = false
                   AND category_id IN
                   <foreach collection="categoryIds" item="categoryId" open="(" separator="," close=")">
                       #{categoryId}
                   </foreach>
            )
            </script>
            """)
    boolean existsByCategoryIds(@Param("categoryIds") Collection<String> categoryIds);

    /**
     * 判断 UOM 是否被非删除物料直接引用；查询无写副作用。
     * @param uomId UOM ID
     * @return 存在引用时返回 true
     */
    @Select("SELECT EXISTS (SELECT 1 FROM mdm_material WHERE deleted = false AND base_uom_id = #{uomId})")
    boolean existsByUomId(@Param("uomId") String uomId);

    /**
     * 判断 UOM Category 是否被非删除物料间接引用；查询无写副作用。
     * @param categoryId UOM Category ID
     * @return 类别内任一 UOM 被引用时返回 true
     */
    @Select("""
            SELECT EXISTS (
                SELECT 1
                  FROM mdm_material m
                  JOIN mdm_uom u ON u.id = m.base_uom_id AND u.deleted = false
                 WHERE m.deleted = false AND u.category_id = #{categoryId}
            )
            """)
    boolean existsByUomCategoryId(@Param("categoryId") String categoryId);

    /**
     * 判断 Dimension 是否被非删除物料通过单位链间接引用；查询无写副作用。
     * @param dimensionId Dimension ID
     * @return 量纲下任一 UOM 被引用时返回 true
     */
    @Select("""
            SELECT EXISTS (
                SELECT 1
                  FROM mdm_material m
                  JOIN mdm_uom u ON u.id = m.base_uom_id AND u.deleted = false
                  JOIN mdm_uom_category c ON c.id = u.category_id AND c.deleted = false
                 WHERE m.deleted = false AND c.dimension_id = #{dimensionId}
            )
            """)
    boolean existsByDimensionId(@Param("dimensionId") String dimensionId);
}
