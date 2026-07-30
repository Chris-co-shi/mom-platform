package io.github.chrisshi.mom.system.infrastructure.persistence.i18n;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import io.github.chrisshi.mom.data.typehandler.PostgresqlJsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Dynamic I18n 单 Locale 不可变发布快照行模型。
 *
 * <p>Release 统一继承 {@link BaseEntity} 并使用独立 String 技术主键；resourceId、releaseVersion、locale
 * 继续由数据库 Unique Constraint 保证业务唯一。生产路径只 Insert/Read，数据库触发器继续拒绝
 * Update/Delete；{@code deleted} 始终保持 false。</p>
 */
@Getter
@Setter
@TableName(value = "system_i18n_release", autoResultMap = true)
public class SystemI18nReleaseEntity extends BaseEntity {
    @TableField("resource_id")
    private String resourceId;

    @TableField("release_version")
    private Long releaseVersion;

    @TableField("locale")
    private String locale;

    @TableField(value = "messages_json", typeHandler = PostgresqlJsonbStringTypeHandler.class)
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
