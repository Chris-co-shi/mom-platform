package io.github.chrisshi.mom.system.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.system.infrastructure.entity.I18nTranslationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * System I18n Translation 的 MyBatis-Plus Mapper。
 *
 * <p>普通查询和 Version CAS 均复用 BaseMapper；Placeholder 合同、Locale 引用及 after-commit 通知不在
 * Mapper 内实现，避免数据访问层拥有业务语义。</p>
 */
@Mapper
public interface I18nTranslationMapper extends MomBaseMapper<I18nTranslationEntity> {
}
