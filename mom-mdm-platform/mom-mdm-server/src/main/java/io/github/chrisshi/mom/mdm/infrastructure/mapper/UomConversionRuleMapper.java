package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.UomConversionRuleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 换算规则版本的单表数据访问入口，属于 MDM Infrastructure 数据库适配边界。
 *
 * <p>只提供 MyBatis-Plus 单表读写；不可变版本发布、停旧建新、换算算法与事务均由 Application 负责。
 * lockVersion 和数据库部分唯一索引兜底并发，数据库不可用时直接失败。</p>
 */
@Mapper
public interface UomConversionRuleMapper extends MomBaseMapper<UomConversionRuleEntity> {
}
