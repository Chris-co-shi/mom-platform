package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计量单位目录的单表数据访问入口，属于 MDM Infrastructure 数据库适配边界。
 *
 * <p>仅由 Application 使用 MyBatis-Plus 完成 CRUD/分页；禁止承载换算、基准单位或生命周期规则。
 * 数据库唯一性和乐观锁是最终并发兜底，基础设施失败不做缓存降级。</p>
 */
@Mapper
public interface UomMapper extends MomBaseMapper<UomEntity> {
}
