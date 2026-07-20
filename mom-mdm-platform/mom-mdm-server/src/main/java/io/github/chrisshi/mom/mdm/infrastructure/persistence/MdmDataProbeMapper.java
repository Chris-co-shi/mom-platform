package io.github.chrisshi.mom.mdm.infrastructure.persistence;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/**
 * MDM PostgreSQL 技术验证 Mapper。
 *
 * <p>继承 MomBaseMapper，Wrapper-only Update 被拒绝。自定义 SQL 示例显式接收更新时间和 Actor，证明
 * 无法经过实体填充的路径必须由 Repository/Mapper 承担审计责任。</p>
 */
@Mapper
public interface MdmDataProbeMapper extends MomBaseMapper<MdmDataProbeEntity> {

    /** 使用显式审计字段执行技术验证更新。 */
    @Update("""
            UPDATE technical_data_probe
               SET probe_value = #{probeValue},
                   updated_at = #{updatedAt},
                   updated_by = #{updatedBy}
             WHERE id = #{id}
               AND deleted = false
            """)
    int updateValueWithExplicitAudit(
            @Param("id") String id,
            @Param("probeValue") String probeValue,
            @Param("updatedAt") Instant updatedAt,
            @Param("updatedBy") String updatedBy);
}
