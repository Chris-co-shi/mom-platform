package io.github.chrisshi.mom.system.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.entity.DictionaryTypeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典类型的 MyBatis-Plus 单表访问入口。
 *
 * <p>Mapper 只提供 Infrastructure CRUD，不承载授权、事务或业务规则。Application 使用类型安全 Wrapper
 * 调用继承方法；禁止 Controller 直接依赖本接口，也不为普通查询创建 Mapper XML。</p>
 */
@Mapper
public interface DictionaryTypeMapper extends MomBaseMapper<DictionaryTypeEntity> {
}
