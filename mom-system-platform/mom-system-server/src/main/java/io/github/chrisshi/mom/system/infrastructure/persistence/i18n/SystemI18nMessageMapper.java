package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Dynamic I18n Draft Message 的 MyBatis-Plus Mapper。
 *
 * <p>普通 INSERT/UPDATE 统一使用 Entity 路径；父资源关系、发布构建和管理分页通过显式列 SQL 保持路径隔离、
 * 有界结果与确定性排序，不接受客户端 SQL 标识符。</p>
 */
@Mapper
public interface SystemI18nMessageMapper extends MomBaseMapper<SystemI18nMessageEntity> {
    /** 按父资源与内部 ID 精确读取 Draft。 */
    SystemI18nMessageEntity selectByResourceAndId(
            @Param("resourceId") String resourceId,
            @Param("messageId") String messageId);

    /** 返回发布构建所需的全部启用 Draft，排序固定。 */
    List<SystemI18nMessageEntity> selectEnabledByResource(@Param("resourceId") String resourceId);

    /** 按受控条件执行 Draft 分页。 */
    List<SystemI18nMessageEntity> selectPage(
            @Param("resourceId") String resourceId,
            @Param("messageKey") String messageKey,
            @Param("locale") String locale,
            @Param("enabled") Boolean enabled,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /** 统计与分页条件完全一致的 Draft 数量。 */
    long countPage(
            @Param("resourceId") String resourceId,
            @Param("messageKey") String messageKey,
            @Param("locale") String locale,
            @Param("enabled") Boolean enabled);
}
