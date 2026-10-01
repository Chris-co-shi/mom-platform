package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 计量单位目录的单表数据访问入口，属于 MDM Infrastructure 数据库适配边界。
 *
 * <p>仅由 Application 使用 MyBatis-Plus 完成 CRUD/分页；禁止承载换算、基准单位或生命周期规则。
 * 数据库唯一性和乐观锁是最终并发兜底，基础设施失败不做缓存降级。</p>
 */
@Mapper
public interface UomMapper extends MomBaseMapper<UomEntity> {
    /**
     * 锁定有效计量单位；调用方必须处于本地事务中，数据库异常直接上抛。
     * @param id 计量单位 ID
     * @return 被行锁锁定的单位；不存在时返回 null
     */
    @Select("""
            SELECT id, code, name, symbol, category_id, reference_unit,
                   ucum_not_applicable_reason, status, created_at, created_by,
                   updated_at, updated_by, version, deleted
              FROM mdm_uom
             WHERE id = #{id} AND deleted = false
             FOR UPDATE
            """)
    UomEntity selectByIdForUpdate(@Param("id") String id);
}
