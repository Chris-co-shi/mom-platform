package io.github.chrisshi.mom.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.data.typehandler.PostgresqlJsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Dynamic I18n 单 Locale 不可变发布快照行模型。
 *
 * <p>Release 属于不可变快照，只继承 {@link BaseAuditEntity} 取得 String 技术主键与创建/更新审计；它不在
 * Java 模型中声明乐观锁或逻辑删除能力。V4 已发布的 {@code version}/{@code deleted} 兼容列保留默认值，
 * 生产路径只 Insert/Read，数据库触发器继续拒绝 Update/Delete。resourceId、releaseVersion、locale 由
 * 数据库 Unique Constraint 保证业务唯一；数据库不可用或触发器拒绝写入时由发布事务整体回滚。</p>
 */
@Getter
@Setter
@TableName(value = "system_i18n_release", autoResultMap = true)
public class SystemI18nReleaseEntity extends BaseAuditEntity {
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
