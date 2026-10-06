package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.I18nMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MDM I18n 消息定义的 MyBatis-Plus Mapper。
 *
 * <p>仅执行本地单表访问；namespace ownership、Key 不可变和启停语义由 I18n Application 负责。</p>
 */
@Mapper
public interface I18nMessageMapper extends MomBaseMapper<I18nMessageEntity> {
}
