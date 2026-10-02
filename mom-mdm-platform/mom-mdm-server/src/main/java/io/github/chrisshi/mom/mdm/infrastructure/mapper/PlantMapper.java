package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.PlantEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Plant 单表数据访问入口。
 *
 * <p>普通 CRUD 复用 MyBatis-Plus；父子写并发需要的主键行锁由显式 SQL 提供。Mapper 只允许被 MDM
 * Application 在本地事务内调用，不承载业务状态判断，数据库不可用或锁等待失败时异常上抛并整体回滚。</p>
 */
@Mapper
public interface PlantMapper extends MomBaseMapper<PlantEntity> {

    /**
     * 按主键锁定有效 Plant，使停用与创建、启用直接子级串行执行。
     * @param id Plant ID
     * @return 已锁定 Plant；不存在或已逻辑删除时返回 null
     */
    @Select("""
            SELECT id, code, name, status, created_at, created_by, updated_at, updated_by, version, deleted
              FROM mdm_plant
             WHERE id = #{id} AND deleted = false
             FOR UPDATE
            """)
    PlantEntity selectByIdForUpdate(@Param("id") String id);
}
