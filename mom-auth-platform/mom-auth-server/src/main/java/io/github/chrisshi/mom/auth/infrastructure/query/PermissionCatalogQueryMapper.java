package io.github.chrisshi.mom.auth.infrastructure.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.chrisshi.mom.auth.application.model.AuthPageParams.PermissionPageParams;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Permission 管理目录的本地关联分页查询。
 *
 * <p>权限页需要同时过滤 Resource 名称与 Permission 字段，单表 Wrapper 无法表达；
 * 因此在 Auth 同一 Schema 做一对一 JOIN，MyBatis-Plus 分页插件负责 Count/Offset。
 * SQL 参数化且排序固定；不参与写事务或运行时 authority 判断。</p>
 */
@Mapper
public interface PermissionCatalogQueryMapper {
    /**
     * 按资源、域、状态和关键字查询权限目录，附带权威资源元数据。
     *
     * @param page 已由 PageAdapter 校验的分页对象
     * @param filters 规范化过滤条件
     * @return 带总数的分页查询行
     */
    @Select("""
        <script>
        SELECT p.id, p.resource_id, r.domain_code, r.resource_code, r.name AS resource_name,
               p.action_code, p.code, p.name, p.description, p.enabled, p.version,
               p.created_at, p.updated_at
        FROM auth_permission p
        JOIN auth_permission_resource r ON r.id = p.resource_id AND r.deleted = false
        WHERE p.deleted = false
        <if test="filters.domainCode != null and filters.domainCode != ''">
          AND r.domain_code = #{filters.domainCode}
        </if>
        <if test="filters.resourceId != null and filters.resourceId != ''">
          AND p.resource_id = #{filters.resourceId}
        </if>
        <if test="filters.enabled != null">
          AND p.enabled = #{filters.enabled}
        </if>
        <if test="filters.keyword != null and filters.keyword != ''">
          AND (p.code ILIKE CONCAT('%', #{filters.keyword}, '%')
            OR p.name ILIKE CONCAT('%', #{filters.keyword}, '%')
            OR p.description ILIKE CONCAT('%', #{filters.keyword}, '%')
            OR r.name ILIKE CONCAT('%', #{filters.keyword}, '%'))
        </if>
        ORDER BY p.code ASC, p.id ASC
        </script>
        """)
    IPage<PermissionCatalogRow> searchCatalog(
        IPage<PermissionCatalogRow> page, @Param("filters") PermissionPageParams filters
    );
}
