package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 发布历史分页查询的 Infrastructure 投影。
 *
 * <p>该类型不是数据库 Entity，也不会进入 Domain 或 Web；它仅承载按 releaseVersion 聚合后的固定查询列，
 * 避免 Mapper 直接依赖领域 Port 内部 Record。</p>
 */
@Getter
@Setter
public class SystemI18nReleaseHistoryRow {
    private Long releaseVersion;
    private Long sourceReleaseVersion;
    private String changeNote;
    private String publishedBy;
    private Instant publishedAt;
    private Integer localeCount;
}
