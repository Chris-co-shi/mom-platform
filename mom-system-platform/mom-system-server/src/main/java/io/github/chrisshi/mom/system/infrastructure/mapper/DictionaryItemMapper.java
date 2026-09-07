package io.github.chrisshi.mom.system.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.entity.DictionaryItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典条目的 MyBatis-Plus 单表访问入口。
 *
 * <p>该接口只适配 PostgreSQL 行模型，不代表领域 Repository。Dictionary Application 负责父类型引用、
 * 启停语义与事务；数据库不可用时异常向上失败关闭。</p>
 */
@Mapper
public interface DictionaryItemMapper extends MomBaseMapper<DictionaryItemEntity> {
}
