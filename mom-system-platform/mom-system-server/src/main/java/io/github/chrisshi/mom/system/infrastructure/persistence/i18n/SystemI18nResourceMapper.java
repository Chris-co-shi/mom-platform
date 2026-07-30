package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Dynamic I18n Resource 的 MyBatis-Plus Mapper。
 *
 * <p>普通 INSERT/UPDATE 使用 MomBaseMapper Entity 路径，以统一主键、审计与乐观锁；发布与回滚使用显式
 * {@code FOR UPDATE} 锁定单 Resource。分页只接受服务端定义的精确过滤条件和固定排序。</p>
 */
@Mapper
public interface SystemI18nResourceMapper extends MomBaseMapper<SystemI18nResourceEntity> {
    /** 按稳定双 Code 读取唯一资源。 */
    SystemI18nResourceEntity selectByCodes(
            @Param("applicationCode") String applicationCode,
            @Param("resourceCode") String resourceCode);

    /** 在当前事务中锁定单 Resource，串行化 Publish/Rollback。 */
    @Select("""
            SELECT id, application_code, resource_code, resource_name, default_locale, enabled,
                   published_version, published_by, published_at, version, description,
                   created_by, created_at, updated_by, updated_at
              FROM system_i18n_resource
             WHERE id = #{id}
             FOR UPDATE
            """)
    SystemI18nResourceEntity selectForUpdate(@Param("id") String id);

    /** 按 applicationCode/状态执行固定排序分页。 */
    List<SystemI18nResourceEntity> selectPage(
            @Param("applicationCode") String applicationCode,
            @Param("enabled") Boolean enabled,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /** 统计与分页条件完全一致的资源记录数。 */
    long countPage(
            @Param("applicationCode") String applicationCode,
            @Param("enabled") Boolean enabled);
}
