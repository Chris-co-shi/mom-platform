package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Dynamic I18n 单 Locale 不可变发布快照行模型。
 *
 * <p>数据库主键是 resourceId、releaseVersion、locale 的复合键，因此该追加型快照不继承通用单主键 Entity。
 * Mapper 只提供显式 INSERT 与 SELECT；数据库触发器继续拒绝 UPDATE/DELETE。messagesJson 在 Mapper XML
 * 中显式转换为 PostgreSQL jsonb，读取时转回文本后由 Repository 做 Fail Closed 解析。</p>
 */
@Getter
@Setter
@TableName("system_i18n_release")
public class SystemI18nReleaseEntity {
    @TableField("resource_id")
    private String resourceId;

    @TableField("release_version")
    private Long releaseVersion;

    @TableField("locale")
    private String locale;

    @TableField("messages_json")
    private String messagesJson;

    @TableField("message_count")
    private Integer messageCount;

    @TableField("fallback_count")
    private Integer fallbackCount;

    @TableField("checksum")
    private String checksum;

    @TableField("source_release_version")
    private Long sourceReleaseVersion;

    @TableField("change_note")
    private String changeNote;

    @TableField("published_by")
    private String publishedBy;

    @TableField("published_at")
    private Instant publishedAt;
}
