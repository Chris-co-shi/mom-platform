package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.PlantEntity;
import org.apache.ibatis.annotations.Mapper;

/** Plant 单表数据访问入口；只允许被 MDM Application 使用，数据库异常保持失败并触发事务回滚。 */
@Mapper
public interface PlantMapper extends MomBaseMapper<PlantEntity> {
}
