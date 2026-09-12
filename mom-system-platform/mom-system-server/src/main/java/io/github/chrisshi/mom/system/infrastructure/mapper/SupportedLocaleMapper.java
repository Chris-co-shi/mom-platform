package io.github.chrisshi.mom.system.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.entity.SupportedLocaleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * SupportedLocale 的 MyBatis-Plus 单表访问入口。
 *
 * <p>Application 负责唯一默认、启停与回退规则；Mapper 不承载业务流程。默认切换所需固定行锁由
 * Application 的类型安全查询调用表达，不维护 XML。</p>
 */
@Mapper
public interface SupportedLocaleMapper extends MomBaseMapper<SupportedLocaleEntity> {
}
