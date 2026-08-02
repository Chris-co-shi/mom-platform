package io.github.chrisshi.mom.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Dynamic I18n Resource 的 MyBatis-Plus 行模型。
 *
 * <p>稳定 applicationCode、resourceCode 和 defaultLocale 创建后不允许更新。System 自有普通业务表
 * 统一继承 {@link BaseEntity}；当前 Resource 没有删除 API，逻辑删除字段始终保持 false。</p>
 */
@Getter
@Setter
@TableName("system_i18n_resource")
public class SystemI18nResourceEntity extends BaseEntity {
    @TableField("application_code")
    private String applicationCode;

    @TableField("resource_code")
    private String resourceCode;

    @TableField("resource_name")
    private String resourceName;

    @TableField("default_locale")
    private String defaultLocale;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("published_version")
    private Long publishedVersion;

    @TableField("published_by")
    private String publishedBy;

    @TableField("published_at")
    private Instant publishedAt;

    /** 允许管理更新显式清空可选说明。 */
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
}
