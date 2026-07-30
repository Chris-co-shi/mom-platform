package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Dynamic I18n 不可变 Release 的 MyBatis Mapper。
 *
 * <p>该 Mapper 虽接入统一 MomBaseMapper 类型治理，但生产路径只调用显式的 INSERT/SELECT。调用方在分配下一版本
 * 前已经持有 Resource 行锁；数据库复合主键和不可变触发器继续作为并发与生命周期最终兜底。</p>
 */
@Mapper
public interface SystemI18nReleaseMapper extends MomBaseMapper<SystemI18nReleaseEntity> {
    /** 在 Resource 行锁保护下分配下一单调发布版本。 */
    long selectNextVersion(@Param("resourceId") String resourceId);

    /** 追加单 Locale Release；禁止 Upsert。 */
    int insertRelease(SystemI18nReleaseEntity entity);

    /** 读取同一资源、同一版本的全部 Locale 快照。 */
    List<SystemI18nReleaseEntity> selectRelease(
            @Param("resourceId") String resourceId,
            @Param("releaseVersion") long releaseVersion);

    /** 按版本倒序读取历史聚合分页。 */
    List<SystemI18nReleaseHistoryRow> selectHistory(
            @Param("resourceId") String resourceId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    /** 统计版本级历史总数。 */
    long countHistory(@Param("resourceId") String resourceId);
}
